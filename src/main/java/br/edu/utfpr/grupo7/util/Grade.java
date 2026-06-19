package br.edu.utfpr.grupo7.util;

import java.util.Random;

/**
 * Núcleo do modelo de propagação de fake news.
 * <p>
 * As três versões (sequencial, paralela e distribuída) usam estes métodos para
 * criar a grade e aplicar a regra de transição. Isso garante que, para os mesmos
 * parâmetros, todas produzam resultados idênticos.
 *
 * @see Estados
 */
public class Grade {

    /**
     * Construtor privado: classe sem instâncias.
     */
    private Grade() {
    }

    /**
     * Cria a matriz inicial da simulação.
     * <p>
     * A maioria das células começa IGNORANTE e uma fração começa como
     * ESPALHADOR, em posições sorteadas. Porte fiel do criar_grade() original
     * em Python: o número de sorteios é int(totalCelulas * percentual) e colisões
     * (mesma célula sorteada mais de uma vez) são permitidas, como no original.
     *
     * @param linhas                 número de linhas da matriz
     * @param colunas                número de colunas da matriz
     * @param percentualEspalhadores fração inicial de espalhadores (ex.: 0.05 = 5%)
     * @param semente                semente do gerador aleatorio
     * @return a matriz inicial, com valores em {@link Estados}
     */
    public static int[][] criarGrade(int linhas, int colunas, double percentualEspalhadores, long semente) {
        Random rng = new Random(semente);
        int[][] grade = new int[linhas][colunas]; // int default = 0 = IGNORANTE

        long totalCelulas = (long) linhas * colunas;
        int totalEspalhadores = (int) (totalCelulas * percentualEspalhadores);

        for (int k = 0; k < totalEspalhadores; k++) {
            int i = rng.nextInt(linhas);
            int j = rng.nextInt(colunas);

            grade[i][j] = Estados.ESPALHADOR;
        }

        return grade;
    }

    /**
     * Conta os vizinhos no estado {@code ESPALHADOR} de uma célula, usando a
     * vizinhança de Moore (até 8 vizinhos) e respeitando os limites da matriz.
     * <p>
     * Usada pelas versões sequencial e paralela, que enxergam a grade global
     * inteira. A versão distribuída usa uma contagem propria com ghost rows.
     *
     * @param grade a matriz atual
     * @param i     índice da linha da célula
     * @param j     índice da coluna da célula
     * @return número de vizinhos no estado Espalhador
     */
    public static int contarVizinhosEspalhadores(int[][] grade, int i, int j) {
        int linhas = grade.length;
        int colunas = grade[0].length;
        int total = 0;

        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                if (di == 0 && dj == 0) continue;

                int ni = i + di;
                int nj = j + dj;

                if (ni >= 0 && ni < linhas && nj >= 0 && nj < colunas) {
                    if (grade[ni][nj] == Estados.ESPALHADOR) {
                        total++;
                    }
                }
            }
        }

        return total;
    }

    /**
     * Aplica a regra de transição a um unico individuo.
     * <p>
     * IGNORANTE → ESPALHADOR se vizinhosEspalhadores ≥ limiar
     * ESPALHADOR → INATIVO
     * INATIVO → INATIVO
     *
     * @param estadoAtual          estado atual da célula
     * @param vizinhosEspalhadores número de vizinhos espalhadores
     * @param limiar               limiar de convencimento
     * @return o proximo estado da célula
     */
    public static int proximoEstado(int estadoAtual, int vizinhosEspalhadores, int limiar) {
        switch (estadoAtual) {
            case Estados.IGNORANTE:
                return (vizinhosEspalhadores >= limiar) ? Estados.ESPALHADOR : Estados.IGNORANTE;
            case Estados.ESPALHADOR:
                return Estados.INATIVO;
            default: // INATIVO
                return Estados.INATIVO;
        }
    }

    /**
     * Conta quantas células existem em cada estado.
     *
     * @param grade a matriz a contabilizar
     * @return vetor com as contagens no formato [IGNORANTE, ESPALHADOR, INATIVO]
     */
    public static long[] contarEstados(int[][] grade) {
        long ignorantes = 0;
        long espalhadores = 0;
        long inativos = 0;

        for (int[] linha : grade) {
            for (int c : linha) {
                if (c == Estados.IGNORANTE) {
                    ignorantes++;
                } else if (c == Estados.ESPALHADOR) {
                    espalhadores++;
                } else {
                    inativos++;
                }
            }
        }

        return new long[]{ignorantes, espalhadores, inativos};
    }

    /**
     * Compara duas grades célula a célula.
     * <p>
     * Usada para verificar que as diferentes versões produzem o mesmo resultado.
     *
     * @param a primeira grade
     * @param b segunda grade
     * @return true somente se forem idênticas
     */
    public static boolean iguais(int[][] a, int[][] b) {
        if (a == null && b == null) return true;

        if (a == null || b == null) return false;

        if (a.length != b.length) return false;

        for (int i = 0; i < a.length; i++) {
            if (!java.util.Arrays.equals(a[i], b[i])) return false;
        }

        return true;
    }

    /**
     * Conta em quantas células as duas grades diferem.
     *
     * @param a primeira grade
     * @param b segunda grade
     * @return numero de células com valores diferentes
     */
    public static long contarDiferencas(int[][] a, int[][] b) {
        long difs = 0;

        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[i].length; j++) {
                if (a[i][j] != b[i][j]) {
                    difs++;
                }
            }
        }

        return difs;
    }
}
