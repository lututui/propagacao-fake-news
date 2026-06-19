package br.edu.utfpr.grupo7;

import br.edu.utfpr.grupo7.util.Grade;
import br.edu.utfpr.grupo7.util.Misc;
import br.edu.utfpr.grupo7.util.Resultado;

/**
 * Versão SEQUENCIAL.
 * <p>
 * Porte fiel do código Python original: a cada geracao e alocada uma NOVA
 * grade (equivalente ao deepcopy), lendo a grade anterior para calcular a
 * próxima.
 * <p>
 * Referência de corretude: as versões paralela e distribuída devem produzir
 * os mesmos resultados.
 *
 * @see Grade
 * @see Resultado
 */
public class Sequencial {
    /**
     * Calcula a próxima geração a partir da grade atual.
     * <p>
     * Aloca uma nova matriz e, para cada célula, aplica a regra de transição
     *
     * @param grade  a grade da geracao atual
     * @param limiar limiar de convencimento da regra de transição
     * @return a grade da proxima geração
     */
    public static int[][] proximaGeracao(int[][] grade, int limiar) {
        int linhas = grade.length;
        int colunas = grade[0].length;
        int[][] nova = new int[linhas][colunas];

        for (int i = 0; i < linhas; i++) {
            for (int j = 0; j < colunas; j++) {
                int viz = Grade.contarVizinhosEspalhadores(grade, i, j);
                nova[i][j] = Grade.proximoEstado(grade[i][j], viz, limiar);
            }
        }

        return nova;
    }

    /**
     * Executa a simulação sequencial completa.
     *
     * @param linhas                 número de linhas da matriz
     * @param colunas                número de colunas da matriz
     * @param geracoes               número máximo de gerações
     * @param percentualEspalhadores percentual inicial de espalhadores
     * @param limiar                 limiar de convencimento da regra de transição
     * @param semente                semente do gerador aleatorio (reprodutibilidade)
     * @param verboso                se true, imprime cabeçalho, estatísticas
     *                               por geracao e o resumo final
     * @return um {@link Resultado}
     */
    public static Resultado executar(int linhas, int colunas, int geracoes,
                                     double percentualEspalhadores, int limiar,
                                     long semente, boolean verboso) {
        int[][] grade = Grade.criarGrade(linhas, colunas, percentualEspalhadores, semente);

        if (verboso) {
            System.out.println("=== SIMULACAO SEQUENCIAL DE PROPAGACAO DE FAKE NEWS ===");
            System.out.printf("Grade: %d x %d (%,d pessoas) | Geracoes: %d | Limiar: %d%n",
                    linhas, colunas, (long) linhas * colunas, geracoes, limiar);
            long[] ini = Grade.contarEstados(grade);
            System.out.printf("Espalhadores iniciais: %,d (%.2f%%)%n%n",
                    ini[1], percentualEspalhadores * 100);
        }

        long t0 = System.nanoTime();
        int feitas = 0;
        long[] contagem;

        for (int g = 0; g < geracoes; g++) {
            grade = proximaGeracao(grade, limiar);
            feitas++;
            contagem = Grade.contarEstados(grade);

            if (verboso) {
                System.out.printf("Geracao %03d | Ignorantes: %,10d | Espalhadores: %,10d | Inativos: %,10d%n",
                        g + 1, contagem[0], contagem[1], contagem[2]);
            }
            if (contagem[1] == 0) {
                if (verboso) {
                    System.out.println("\nPropagacao encerrada: nao ha mais espalhadores.");
                }
                break;
            }
        }

        double tempo = (System.nanoTime() - t0) / 1e9;
        Resultado resultado = new Resultado(grade, tempo, feitas);

        if (verboso) {
            resultado.imprimirResumo();
        }

        return resultado;
    }

    /**
     * Ponto de entrada para execução da versão sequencial.
     *
     * @param args argumentos opcionais, nesta ordem:
     *             {@code [0]} linhas (padrão 100);
     *             {@code [1]} colunas (padrão 100);
     *             {@code [2]} gerações (padrão 50);
     *             {@code [3]} percentual de espalhadores (padrão 0.05);
     *             {@code [4]} limiar (padrão 3);
     *             {@code [5]} semente (padrão 42)
     */
    public static void main(String[] args) {
        int linhas = Misc.arg(args, 0, 100);
        int colunas = Misc.arg(args, 1, 100);
        int geracoes = Misc.arg(args, 2, 50);
        double pct = args.length > 3 ? Double.parseDouble(args[3]) : 0.05;
        int limiar = Misc.arg(args, 4, 3);
        long semente = args.length > 5 ? Long.parseLong(args[5]) : 42L;

        executar(linhas, colunas, geracoes, pct, limiar, semente, true);
    }
}