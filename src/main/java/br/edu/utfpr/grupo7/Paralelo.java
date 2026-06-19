package br.edu.utfpr.grupo7;

import br.edu.utfpr.grupo7.util.Grade;
import br.edu.utfpr.grupo7.util.Misc;
import br.edu.utfpr.grupo7.util.Resultado;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

/**
 * Versão PARALELA com THREADS.
 * <p>
 * Divisão do trabalho: a matriz é dividida em faixas de linhas, uma faixa por
 * thread; cada thread calcula apenas as linhas da sua faixa.
 * <p>
 * Consistência: existem duas grades: "atual" (somente leitura durante a geração)
 * e "próxima" (escrita). Como cada thread escreve apenas nas suas respectivas
 * linhas de "proxima" e todas leem de "atual", não há condição de corrida.
 * <p>
 * Synchronization ({@link CyclicBarrier}): ao terminar a sua faixa, cada thread
 * espera na barreira. Quando todas chegam, a ação de barreira (executada por uma
 * unica thread) troca os buffers, conta os estados e decide se a propagação
 * terminou. Só então as threads avançam para a proxima geracao, garantindo que
 * nenhuma comece a geracao G+1 antes de G estar concluída.
 *
 * @see Grade
 * @see Resultado
 */
public class Paralelo {
    /**
     * Número de linhas da matriz.
     */
    private final int linhas;

    /**
     * Número de colunas da matriz.
     */
    private final int colunas;

    /**
     * Número máximo de gerações a simular.
     */
    private final int geracoes;

    /**
     * Limiar de convencimento de transição.
     */
    private final int limiar;

    /**
     * Número de threads usadas no calculo.
     */
    private final int nThreads;

    /**
     * Se true, imprime cabecalho, estatisticas por geração e resumo.
     */
    private final boolean verboso;

    /**
     * Buffer de leitura da geração corrente
     */
    private volatile int[][] gradeAtual;

    /**
     * Buffer de escrita da proxima geração.
     */
    private volatile int[][] gradeProxima;

    /**
     * Após a barreira, sinaliza se a propagação terminou (0 espalhadores).
     */
    private volatile boolean parar = false;

    /**
     * Número de gerações executadas.
     */
    private int geracoesExecutadas = 0;

    /**
     * Contagem de estados da geração corrente
     * [IGNORANTE, ESPALHADOR, INATIVO]}
     */
    private long[] contagemAtual;

    /**
     * Cria uma simulação paralela com os parâmetros informados.
     *
     * @param linhas   número de linhas da matriz
     * @param colunas  número de colunas da matriz
     * @param geracoes número máximo de gerações
     * @param limiar   limiar de convencimento da regra de transição
     * @param nThreads número de threads (faixas de linhas)
     * @param verboso  se true, imprime o progresso e o resumo final
     */
    public Paralelo(int linhas, int colunas, int geracoes, int limiar,
                    int nThreads, boolean verboso) {
        this.linhas = linhas;
        this.colunas = colunas;
        this.geracoes = geracoes;
        this.limiar = limiar;
        this.nThreads = nThreads;
        this.verboso = verboso;
    }

    /**
     * Executa a simulação paralela.
     * <p>
     * Cria a grade inicial, dispara as threads, sincroniza-as por geracao com a
     * barreira e mede o tempo.
     *
     * @param percentualEspalhadores percentual inicial de espalhadores
     * @param semente                semente do gerador aleatorio (reprodutibilidade)
     * @return o {@link Resultado} (grade final, tempo e gerações executadas)
     */
    public Resultado executar(double percentualEspalhadores, long semente) {
        gradeAtual = Grade.criarGrade(linhas, colunas, percentualEspalhadores, semente);
        gradeProxima = new int[linhas][colunas];
        contagemAtual = Grade.contarEstados(gradeAtual);

        if (verboso) {
            System.out.println("=== SIMULACAO PARALELA (THREADS) DE PROPAGACAO DE FAKE NEWS ===");
            System.out.printf("Grade: %d x %d | Geracoes: %d | Limiar: %d | Threads: %d%n%n",
                    linhas, colunas, geracoes, limiar, nThreads);
        }

        CyclicBarrier barreira = new CyclicBarrier(nThreads, () -> {
            // Ação de barreira: roda quando TODAS as threads terminam a geração.
            int[][] tmp = gradeAtual;
            gradeAtual = gradeProxima; // a grade recém-calculada vira a "atual"
            gradeProxima = tmp;        // o buffer antigo será sobrescrito na proxima
            geracoesExecutadas++;
            contagemAtual = Grade.contarEstados(gradeAtual);

            if (verboso) {
                System.out.printf("Geracao %03d | Ignorantes: %,10d | Espalhadores: %,10d | Inativos: %,10d%n",
                        geracoesExecutadas, contagemAtual[0], contagemAtual[1], contagemAtual[2]);
            }

            if (contagemAtual[1] == 0) {
                parar = true;
            }
        });

        // Divisão das linhas em faixas.
        Thread[] threads = new Thread[nThreads];
        int base = linhas / nThreads;
        int resto = linhas % nThreads;
        int inicio = 0;

        long t0 = System.nanoTime();

        for (int t = 0; t < nThreads; t++) {
            int qtd = base + (t < resto ? 1 : 0);
            final int linhaInicial = inicio;
            final int linhaFinal = inicio + qtd;
            inicio = linhaFinal;

            threads[t] = new Thread(() -> trabalhar(linhaInicial, linhaFinal, barreira),
                    "worker-" + t);
            threads[t].start();
        }

        for (Thread th : threads) {
            try {
                th.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        double tempo = (System.nanoTime() - t0) / 1e9;
        Resultado resultado = new Resultado(gradeAtual, tempo, geracoesExecutadas);

        if (verboso) {
            resultado.imprimirResumo();
        }

        return resultado;
    }

    /**
     * Código executado por cada thread.
     * <p>
     * Para cada geração, calcula a sua faixa de linhas lendo de "gradeAtual" e
     * escrevendo em "gradeProxima", aguarda na barreira e, ao ser liberada,
     * verifica se deve parar.
     *
     * @param linhaInicial primeira linha da faixa (inclusiva)
     * @param linhaFinal   limite da faixa (exclusivo)
     * @param barreira     barreira compartilhada que sincroniza o fim de cada geracao
     */
    private void trabalhar(int linhaInicial, int linhaFinal, CyclicBarrier barreira) {
        for (int g = 0; g < geracoes; g++) {
            int[][] src = gradeAtual;
            int[][] dst = gradeProxima;

            for (int i = linhaInicial; i < linhaFinal; i++) {
                for (int j = 0; j < colunas; j++) {
                    int viz = Grade.contarVizinhosEspalhadores(src, i, j);
                    dst[i][j] = Grade.proximoEstado(src[i][j], viz, limiar);
                }
            }

            try {
                barreira.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                return;
            }

            if (parar) {
                break;
            }
        }
    }

    /**
     * Ponto de entrada para execução da versão paralela.
     *
     * @param args argumentos opcionais, nesta ordem:
     *             {@code [0]} linhas (padrão 100);
     *             {@code [1]} colunas (padrão 100);
     *             {@code [2]} gerações (padrão 50);
     *             {@code [3]} percentual de espalhadores (padrão 0.05);
     *             {@code [4]} limiar (padrão 3);
     *             {@code [5]} numero de threads (padrão = núcleos disponíveis);
     *             {@code [6]} semente (padrão 42)
     */
    public static void main(String[] args) {
        int linhas = Misc.arg(args, 0, 100);
        int colunas = Misc.arg(args, 1, 100);
        int geracoes = Misc.arg(args, 2, 50);
        double pct = args.length > 3 ? Double.parseDouble(args[3]) : 0.05;
        int limiar = Misc.arg(args, 4, 3);
        int nThreads = Misc.arg(args, 5, Runtime.getRuntime().availableProcessors());
        long semente = args.length > 6 ? Long.parseLong(args[6]) : 42L;

        new Paralelo(linhas, colunas, geracoes, limiar, nThreads, true)
                .executar(pct, semente);
    }
}
