package br.edu.utfpr.grupo7.util;

/**
 * Resultado de uma simulação: a grade final, o tempo de execução e o número de
 * geracoes executadas.
 *
 * @see Grade
 * @see Estados
 */
public class Resultado {
    /**
     * Grade no estado final da simulação.
     */
    private final int[][] gradeFinal;

    /**
     * Tempo de execução em segundos.
     */
    private final double tempoSegundos;

    /**
     * Número de gerações efetivamente executadas
     * Pode ser menor que o pedido se a propagação terminar antes.
     */
    private final int geracoes;

    /**
     * Cria um resultado de simulação.
     *
     * @param gradeFinal    grade no estado final
     * @param tempoSegundos tempo de execução, em segundos
     * @param geracoes      numero de gerações executadas
     */
    public Resultado(int[][] gradeFinal, double tempoSegundos, int geracoes) {
        this.gradeFinal = gradeFinal;
        this.tempoSegundos = tempoSegundos;
        this.geracoes = geracoes;
    }

    /**
     * Retorna a grade no estado final da simulação.
     *
     * @return a grade final
     */
    public int[][] gradeFinal() {
        return gradeFinal;
    }

    /**
     * Retorna o tempo de execução.
     *
     * @return o tempo, em segundos
     */
    public double tempoSegundos() {
        return tempoSegundos;
    }

    /**
     * Retorna o número de geracoes efetivamente executadas.
     *
     * @return o número de gerações
     */
    public int geracoes() {
        return geracoes;
    }

    /**
     * Imprime o bloco de resultado no console.
     */
    public void imprimirResumo() {
        long[] c = Grade.contarEstados(gradeFinal);
        long total = c[0] + c[1] + c[2];

        System.out.println("\n=== RESULTADO FINAL ===");
        System.out.printf("Tempo total: %.4f s | Geracoes executadas: %d%n", tempoSegundos, geracoes);
        System.out.printf("Ignorantes:   %,12d (%.2f%%)%n", c[0], 100.0 * c[0] / total);
        System.out.printf("Espalhadores: %,12d (%.2f%%)%n", c[1], 100.0 * c[1] / total);
        System.out.printf("Inativos:     %,12d (%.2f%%)%n", c[2], 100.0 * c[2] / total);
    }
}
