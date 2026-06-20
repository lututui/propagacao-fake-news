package br.edu.utfpr.grupo7.util;

import java.util.Arrays;
import java.util.Locale;

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
     * Converte uma lista de números decimais separados por vírgula em vetor.
     *
     * @param csv texto no formato "p1,p2,p3" (espaços são ignorados)
     * @return vetor com os valores lidos
     */
    public static double[] parseDoubles(String csv) {
        String[] partes = csv.split(",");
        double[] v = new double[partes.length];
        for (int i = 0; i < partes.length; i++) {
            v[i] = Double.parseDouble(partes[i].trim());
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


    /**
     * Imprime uma linha formatada da tabela de resultados.
     *
     * @param versao      nome da versão (ex.: "Sequencial", "Paralela", "Distribuída")
     * @param paralelismo descrição do grau de paralelismo (ex.: "4 threads")
     * @param tempo       tempo de execução, em segundos
     * @param speedup     speedup em relação a sequencial
     * @param eficiencia  eficiência (speedup / p); {@code Double.NaN} exibe "-"
     *                    (caso da sequencial)
     * @param ok          {@code true} se a grade final confere com a sequencial
     */
    public static void linha(String versao, String paralelismo, double tempo,
                              double speedup, double eficiencia, boolean ok) {
        String efic = Double.isNaN(eficiencia) ? "-" : String.format(Locale.US, "%.2f", eficiencia);
        String okStr = ok ? "sim" : "NAO!";
        System.out.printf(Locale.US, "%-13s %-12s %-12.4f %-9.2f %-11s %s%n",
                versao, paralelismo, tempo, speedup, efic, okStr);
    }

    /**
     * Formata um percentual sem notação cientifica, removendo zeros a direita
     * (ex.: 0.0005 ≥ "0.0005"). Usado nos cabeçalhos e no CSV.
     *
     * @param v valor a formatar
     * @return o valor como texto legível
     */
    public static String fmt(double v) {
        String s = String.format(Locale.US, "%.6f", v);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            if (s.endsWith(".")) {
                s = s.substring(0, s.length() - 1);
            }
        }
        return s;
    }

    private Misc() {}
}
