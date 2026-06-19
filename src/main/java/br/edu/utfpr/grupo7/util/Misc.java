package br.edu.utfpr.grupo7.util;

import java.util.Arrays;

public class Misc {
    /**
     * Lê um argumento inteiro posicional, devolvendo um valor padrão se ausente.
     *
     * @param a   vetor de argumentos
     * @param i   índice do argumento
     * @param def valor padrão caso o argumento nao exista
     * @return o inteiro lido ou {@code def}
     */
    public static int arg(String[] a, int i, int def) {
        return a.length > i ? Integer.parseInt(a[i]) : def;
    }

    /**
     * Converte uma lista de inteiros separados por vírgula num vetor.
     *
     * @param csv texto no formato "n1,n2,n3" (espaços são ignorados)
     * @return vetor com os inteiros lidos
     */
    public static int[] parseInts(String csv) {
        String[] partes = csv.split(",");
        int[] v = new int[partes.length];

        for (int i = 0; i < partes.length; i++) {
            v[i] = Integer.parseInt(partes[i].trim());
        }

        return v;
    }

    /**
     * Gera uma String com o character repetido n vezes.
     *
     * @param c caractere a repetir
     * @param n numero de repetições
     * @return String resultante
     */
    public static String repetir(char c, int n) {
        char[] v = new char[n];

        Arrays.fill(v, c);

        return new String(v);
    }

    private Misc() {}
}
