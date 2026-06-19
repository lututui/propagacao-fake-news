package br.edu.utfpr.grupo7;


import br.edu.utfpr.grupo7.util.Grade;
import br.edu.utfpr.grupo7.util.Misc;
import br.edu.utfpr.grupo7.util.Resultado;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark que compara as três versões (sequencial, paralela e distribuída)
 * no mesmo cenário, calculando speedup e eficiência em relação a sequencial
 * e verificando a correção.
 * <p>
 * Para cada tamanho e configuração executa várias repetições e reporta o
 * MENOR tempo. Os resultados são impressos em tabela e salvos em
 * {@code resultados/benchmark_completo.csv}.
 * <p>
 * BenchmarkCompleto <geracoes> <pct> <limiar> <semente> <reps> <tamanhos> <threads> <workers>
 *
 * @see Sequencial
 * @see Paralelo
 * @see Distribuido
 */
public class Benchmark {
    /**
     * Ponto de entrada: roda o benchmark das tres versões e gera o CSV.
     *
     * @param args argumentos opcionais, nesta ordem:
     *             {@code [0]} gerações (padrão 40);
     *             {@code [1]} percentual inicial de espalhadores (padrão 0.0005);
     *             {@code [2]} limiar de convencimento (padrão 1);
     *             {@code [3]} semente (padrão 42);
     *             {@code [4]} repetições por configuração (padrão 3);
     *             {@code [5]} tamanhos de grade, separados por vírgula (padrão 500,1000);
     *             {@code [6]} números de threads, separados por vírgula (padrão 1,2,4);
     *             {@code [7]} números de workers, separados por vírgula (padrão 1,2,4)
     * @throws Exception se a simulação distribuída ou a escrita do CSV falharem
     */
    public static void main(String[] args) throws Exception {
        int geracoes = args.length > 0 ? Integer.parseInt(args[0]) : 40;
        double pct = args.length > 1 ? Double.parseDouble(args[1]) : 0.0005;
        int limiar = args.length > 2 ? Integer.parseInt(args[2]) : 1;
        long semente = args.length > 3 ? Long.parseLong(args[3]) : 42L;
        int reps = args.length > 4 ? Integer.parseInt(args[4]) : 3;
        int[] tamanhos = args.length > 5 ? Misc.parseInts(args[5]) : new int[]{500, 1000};
        int[] threads = args.length > 6 ? Misc.parseInts(args[6]) : new int[]{1, 2, 4};
        int[] workers = args.length > 7 ? Misc.parseInts(args[7]) : new int[]{1, 2, 4};

        System.out.println("=== BENCHMARK: SEQUENCIAL x PARALELA x DISTRIBUIDA ===");
        System.out.printf("Nucleos disponiveis (JVM): %d%n", Runtime.getRuntime().availableProcessors());
        System.out.printf("Geracoes=%d  pct=%s  limiar=%d  semente=%d  repeticoes=%d%n%n",
                geracoes, pct, limiar, semente, reps);

        // Aquecimento do JIT (descartado)
        Sequencial.executar(400, 400, geracoes, pct, limiar, semente, false);
        new Paralelo(400, 400, geracoes, limiar, 2, false).executar(pct, semente);

        Path dir = Paths.get("resultados");
        Files.createDirectories(dir);
        Path csv = dir.resolve("benchmark_completo.csv");

        int porta = 1099;

        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(csv))) {
            w.println("tamanho,versao,paralelismo,tempo_s,speedup,eficiencia,correto");

            for (int n : tamanhos) {
                System.out.println(Misc.repetir('=', 78));
                System.out.printf("GRADE %d x %d%n", n, n);
                System.out.println(Misc.repetir('-', 78));
                System.out.printf("%-13s %-12s %-12s %-9s %-11s %s%n",
                        "Versao", "Paralelismo", "Tempo(s)", "Speedup", "Eficiencia", "OK");
                System.out.println(Misc.repetir('-', 78));

                // --- Sequencial ---
                double tseq = Double.MAX_VALUE;
                int[][] gradeSeq = null;
                for (int r = 0; r < reps; r++) {
                    Resultado res = Sequencial.executar(n, n, geracoes, pct, limiar, semente, false);
                    tseq = Math.min(tseq, res.tempoSegundos());
                    gradeSeq = res.gradeFinal();
                }
                linha("Sequencial", "-", tseq, 1.0, Double.NaN, true);
                w.printf(Locale.US, "%d,Sequencial,1,%.6f,1.0000,1.0000,true%n", n, tseq);

                // --- Paralela ---
                for (int t : threads) {
                    double tpar = Double.MAX_VALUE;
                    int[][] gradePar = null;

                    for (int r = 0; r < reps; r++) {
                        Resultado res = new Paralelo(n, n, geracoes, limiar, t, false).executar(pct, semente);
                        tpar = Math.min(tpar, res.tempoSegundos());
                        gradePar = res.gradeFinal();
                    }

                    double sp = tseq / tpar;
                    boolean ok = Grade.iguais(gradePar, gradeSeq);

                    linha("Paralela", t + " threads", tpar, sp, sp / t, ok);
                    w.printf(Locale.US, "%d,Paralela,%d,%.6f,%.4f,%.4f,%s%n", n, t, tpar, sp, sp / t, ok);
                }

                // --- Distribuida (RMI) ---
                for (int nw : workers) {
                    porta++;
                    List<Process> procs = lancarTrabalhadores(nw, porta);
                    double tdist = Double.MAX_VALUE;
                    int[][] gradeDist = null;
                    boolean ok;

                    try {
                        Thread.sleep(900);

                        for (int r = 0; r < reps; r++) {
                            Resultado res = Distribuido.simular(n, n, geracoes, pct, limiar,
                                    nw, semente, porta, false, false);
                            tdist = Math.min(tdist, res.tempoSegundos());
                            gradeDist = res.gradeFinal();
                        }

                        ok = Grade.iguais(gradeDist, gradeSeq);
                    } finally {
                        encerrar(procs);
                    }

                    double sp = tseq / tdist;
                    linha("Distribuida", nw + " workers", tdist, sp, sp / nw, ok);
                    w.printf(Locale.US, "%d,Distribuida,%d,%.6f,%.4f,%.4f,%s%n", n, nw, tdist, sp, sp / nw, ok);
                }
            }
        }

        System.out.println(Misc.repetir('=', 78));
        System.out.println("\nCSV salvo em: " + csv.toAbsolutePath());
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
    private static void linha(String versao, String paralelismo, double tempo,
                              double speedup, double eficiencia, boolean ok) {
        String efic = Double.isNaN(eficiencia) ? "-" : String.format(Locale.US, "%.2f", eficiencia);
        String okStr = ok ? "sim" : "NAO!";
        System.out.printf(Locale.US, "%-13s %-12s %-12.4f %-9.2f %-11s %s%n",
                versao, paralelismo, tempo, speedup, efic, okStr);
    }

    /**
     * Lança nWorkers processos JVM separados, cada um executando
     * {@link br.edu.utfpr.grupo7.distribuido.WorkerServer} na porta informada.
     * <p>
     * Reutiliza o mesmo executável Java e classpath do processo atual. A saída
     * dos workers e redirecionada para um arquivo temporário.
     *
     * @param nWorkers numero de trabalhadores a lançar
     * @param porta    porta do registro RMI usada por esta rodada
     * @return lista de processos lançados (para posterior encerramento)
     * @throws IOException se algum processo não puder ser iniciado
     */
    private static List<Process> lancarTrabalhadores(int nWorkers, int porta) throws IOException {
        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        String classpath = System.getProperty("java.class.path");

        File log = File.createTempFile("workers_" + porta + "_", ".log");
        log.deleteOnExit();

        List<Process> procs = new ArrayList<>(nWorkers);

        for (int k = 0; k < nWorkers; k++) {
            ProcessBuilder pb = new ProcessBuilder(
                    javaBin, "-cp", classpath, "br.edu.utfpr.grupo7.distribuido.WorkerServer",
                    String.valueOf(k), String.valueOf(porta));
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log));
            procs.add(pb.start());
        }

        return procs;
    }

    /**
     * Encerra os workers.
     * <p>
     * Solicita o termino de cada processo e, se algum não encerrar dentro do
     * tempo limite, forca o encerramento.
     *
     * @param procs processos a encerrar
     */
    private static void encerrar(List<Process> procs) {
        for (Process p : procs) {
            p.destroy();
        }

        for (Process p : procs) {
            try {
                if (!p.waitFor(3, TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                p.destroyForcibly();
            }
        }
    }
}
