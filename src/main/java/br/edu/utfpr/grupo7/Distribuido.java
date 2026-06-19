package br.edu.utfpr.grupo7;

import br.edu.utfpr.grupo7.distribuido.GridService;
import br.edu.utfpr.grupo7.util.Grade;
import br.edu.utfpr.grupo7.util.Misc;
import br.edu.utfpr.grupo7.util.Resultado;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Coordenador da versão distribuída.
 * <p>
 * Cria a grade inicial
 * Divide a grade em faixas de linhas e envia uma banda para cada worker
 * Coleta as linhas de fronteira atuais de cada banda e manda TODOS os
 * workers calcularem a proxima geração (a cada geração)
 * Soma as contagens e avalia o critério de parada.
 *
 * @see GridService
 * @see br.edu.utfpr.grupo7.distribuido.WorkerImpl
 */
public class Distribuido {

    /**
     * Executa a simulação distribuída e devolve o resultado.
     * <p>
     * Conecta-se aos workers registrados no RMI, distribui as bandas e roda o
     * laco de geracoes com disparo concorrente do cálculo (uma thread por
     * worker).
     * <p>
     * O tempo medido cobre apenas o laco de geracoes.
     *
     * @param linhas          numero de linhas da matriz
     * @param colunas         numero de colunas da matriz
     * @param geracoes        numero máximo de gerações
     * @param pct             percentual inicial de espalhadores
     * @param limiar          limiar de convencimento da regra de transição
     * @param nWorkers        numero de trabalhadores (bandas)
     * @param semente         semente do gerador aleatorio (reprodutibilidade)
     * @param porta           porta do registro RMI onde os trabalhadores estão
     * @param verboso         se true, imprime cabeçalho e estatísticas por geracao
     * @param encerrarWorkers se true, solicita o encerramento dos trabalhadores ao final
     * @return um {@link Resultado}
     * @throws Exception se a comunicação RMI ou alguma tarefa de cálculo falhar
     */
    public static Resultado simular(int linhas, int colunas, int geracoes, double pct,
                                    int limiar, int nWorkers, long semente, int porta,
                                    boolean verboso, boolean encerrarWorkers) throws Exception {

        Registry registro = LocateRegistry.getRegistry("127.0.0.1", porta);

        final GridService[] workers = new GridService[nWorkers];
        for (int k = 0; k < nWorkers; k++) {
            workers[k] = conectar(registro, "FakeNewsWorker_" + k);
        }

        int[][] grade = Grade.criarGrade(linhas, colunas, pct, semente);

        if (verboso) {
            System.out.println("=== SIMULACAO DISTRIBUIDA (RMI) DE PROPAGACAO DE FAKE NEWS ===");
            System.out.printf("Grade: %d x %d | Geracoes: %d | Limiar: %d | Trabalhadores: %d%n%n",
                    linhas, colunas, geracoes, limiar, nWorkers);
        }

        // Divisão em faixas
        int[] inicios = new int[nWorkers];
        int[] tamanhos = new int[nWorkers];
        int base = linhas / nWorkers;
        int resto = linhas % nWorkers;
        int pos = 0;

        for (int k = 0; k < nWorkers; k++) {
            int qtd = base + (k < resto ? 1 : 0);
            inicios[k] = pos;
            tamanhos[k] = qtd;
            int[][] banda = new int[qtd][];

            for (int r = 0; r < qtd; r++) {
                banda[r] = grade[pos + r].clone();
            }

            workers[k].configurar(banda, pos, linhas, limiar);
            pos += qtd;
        }

        // Pool com uma thread por trabalhador para disparar as chamadas RMI de
        // cálculo em paralelo.
        ExecutorService pool = Executors.newFixedThreadPool(nWorkers);

        long t0 = System.nanoTime();
        int feitas = 0;

        for (int g = 0; g < geracoes; g++) {
            // Fase 1: coletar fronteiras atuais de todas as bandas.
            final int[][] topos = new int[nWorkers][];
            final int[][] bases = new int[nWorkers][];

            for (int k = 0; k < nWorkers; k++) {
                topos[k] = workers[k].linhaSuperior();
                bases[k] = workers[k].linhaInferior();
            }

            // Fase 2: cada worker calcula a sua banda.
            List<Callable<long[]>> tarefas = new ArrayList<>(nWorkers);
            for (int k = 0; k < nWorkers; k++) {
                final int idx = k;
                final int[] ghostTop = (k > 0) ? bases[k - 1] : null;
                final int[] ghostBottom = (k < nWorkers - 1) ? topos[k + 1] : null;

                tarefas.add(() -> workers[idx].computarGeracao(ghostTop, ghostBottom));
            }

            long ignorantes = 0, espalhadores = 0, inativos = 0;
            // invokeAll bloqueia até todos terminarem.
            List<Future<long[]>> futuros = pool.invokeAll(tarefas);

            for (Future<long[]> f : futuros) {
                long[] c = f.get();
                ignorantes += c[0];
                espalhadores += c[1];
                inativos += c[2];
            }

            feitas++;

            if (verboso) {
                System.out.printf("Geracao %03d | Ignorantes: %,10d | Espalhadores: %,10d | Inativos: %,10d%n",
                        feitas, ignorantes, espalhadores, inativos);
            }

            if (espalhadores == 0) {
                if (verboso) {
                    System.out.println("\nPropagacao encerrada: nao ha mais espalhadores.");
                }
                break;
            }
        }

        double tempo = (System.nanoTime() - t0) / 1e9;
        pool.shutdown();

        // Remonta a grade final a partir das bandas.
        int[][] gradeFinal = new int[linhas][];
        for (int k = 0; k < nWorkers; k++) {
            int[][] banda = workers[k].coletarBanda();

            if (tamanhos[k] >= 0) {
                System.arraycopy(banda, 0, gradeFinal, inicios[k], tamanhos[k]);
            }
        }

        if (encerrarWorkers) {
            for (GridService w : workers) {
                try {
                    w.encerrar();
                } catch (Exception ignored) {
                }
            }
        }

        return new Resultado(gradeFinal, tempo, feitas);
    }

    /**
     * Ponto de entrada da versão distribuída.
     * <p>
     * Faz o parsing dos argumentos, chama simular e verifica a correção
     * executando a versão sequencial com os mesmos parâmetros e comparando
     * as grades, célula a célula. A verificação ocorre FORA da janela de
     * tempo medido.
     *
     * @param args argumentos opcionais, nesta ordem:
     *             {@code [0]} linhas (padrão 100);
     *             {@code [1]} colunas (padrão 100);
     *             {@code [2]} gerações (padrão 50);
     *             {@code [3]} percentual de espalhadores (padrão 0.05);
     *             {@code [4]} limiar (padrão 3);
     *             {@code [5]} numero de trabalhadores (padrão 4);
     *             {@code [6]} semente (padrão 42);
     *             {@code [7]} porta do registro RMI (padrão 1099);
     *             {@code [8]} verboso (padrão true)
     * @throws Exception se a simulação ou a verificação falharem
     */
    public static void main(String[] args) throws Exception {
        int linhas = Misc.arg(args, 0, 100);
        int colunas = Misc.arg(args, 1, 100);
        int geracoes = Misc.arg(args, 2, 50);
        double pct = args.length > 3 ? Double.parseDouble(args[3]) : 0.05;
        int limiar = Misc.arg(args, 4, 3);
        int nWorkers = Misc.arg(args, 5, 4);
        long semente = args.length > 6 ? Long.parseLong(args[6]) : 42L;
        int porta = Misc.arg(args, 7, 1099);
        boolean verboso = args.length <= 8 || Boolean.parseBoolean(args[8]);

        Resultado resultado = simular(linhas, colunas, geracoes, pct, limiar,
                nWorkers, semente, porta, verboso, true);

        // Verificação
        int[][] gradeSeq = Sequencial
                .executar(linhas, colunas, geracoes, pct, limiar, semente, false)
                .gradeFinal();
        long difs = Grade.contarDiferencas(resultado.gradeFinal(), gradeSeq);
        boolean correto = (difs == 0);

        if (verboso) {
            resultado.imprimirResumo();
            System.out.printf("Verificacao vs sequencial: %d celulas diferentes -> %s%n",
                    difs, correto ? "IDENTICO (correto)" : "DIVERGENTE (INCORRETO)");
        } else {
            System.out.printf("DIST_RESULT tempo=%.6f geracoes=%d correto=%b difs=%d%n",
                    resultado.tempoSegundos(), resultado.geracoes(), correto, difs);
        }
    }

    /**
     * Obtém o stub remoto de um worker.
     *
     * @param registro registro RMI
     * @param nome     nome do worker ("FakeNewsWorker_i")
     * @return {@link GridService} do worker
     * @throws Exception se não for possível conectar
     */
    private static GridService conectar(Registry registro, String nome) throws Exception {
        Exception ultima = null;

        for (int tentativa = 0; tentativa < 50; tentativa++) {
            try {
                return (GridService) registro.lookup(nome);
            } catch (Exception e) {
                ultima = e;
                Thread.sleep(20);
            }
        }
        throw new RuntimeException("Nao foi possivel conectar a " + nome, ultima);
    }
}
