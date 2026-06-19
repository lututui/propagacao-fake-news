package br.edu.utfpr.grupo7.distribuido;


import br.edu.utfpr.grupo7.util.Estados;
import br.edu.utfpr.grupo7.util.Grade;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

/**
 * Implementação RMI de um worker
 * <p>
 * Cada worker é responsável por uma faixa de linhas (banda) da matriz global
 * e a mantém entre as gerações.
 * <p>
 * No cálculo da próxima geração, as linhas internas usam vizinhos da própria
 * banda, enquanto as linhas de fronteira usam as linhas-fantasma (ghost rows)
 * recebidas dos vizinhos. Nas bordas da matriz (topo do primeiro e base do
 * último) não há ghost row.
 *
 * @see GridService
 */
public class WorkerImpl extends UnicastRemoteObject implements GridService {
    /**
     * Banda local
     * Faixa de linhas (linhas x colunas) sob responsabilidade deste worker.
     */
    private int[][] banda;

    /**
     * Número de colunas da matriz.
     */
    private int colunas;

    /**
     * Limiar da regra de transição (minimo de vizinhos espalhadores).
     */
    private int limiar;

    /**
     * Indica se há banda vizinha acima
     * false significa borda global superior.
     */
    private boolean temTopo;

    /**
     * Indica se há banda vizinha abaixo;
     * false significa borda global inferior.
     */
    private boolean temBase;

    /**
     * Cria e exporta o objeto remoto.
     *
     * @throws RemoteException se a exportação do objeto RMI falhar
     */
    public WorkerImpl() throws RemoteException {
        super();
    }

    /**
     * Configura a banda inicial deste worker.
     *
     * @param banda              sub-matriz (h x colunas)
     * @param linhaGlobalInicial índice global da primeira linha da banda
     * @param totalLinhas        numero total de linhas da matriz global
     * @param limiar             limiar de convencimento da regra de transição
     */
    @Override
    public synchronized void configurar(int[][] banda, int linhaGlobalInicial, int totalLinhas, int limiar) {
        this.banda = banda;
        this.colunas = banda[0].length;
        this.limiar = limiar;
        this.temTopo = linhaGlobalInicial > 0;
        this.temBase = (linhaGlobalInicial + banda.length) < totalLinhas;
    }

    /**
     * Primeira linha (topo) da banda no estado ATUAL
     *
     * @return Ghost row inferior para vizinho de cima.
     */
    @Override
    public synchronized int[] linhaSuperior() {
        return banda[0].clone();
    }

    /**
     * Última linha (base) da banda no estado ATUAL
     *
     * @return Ghost row superior para vizinho de baixo.
     */
    @Override
    public synchronized int[] linhaInferior() {
        return banda[banda.length - 1].clone();
    }

    /**
     * Calcula a proxima geracao desta banda. Atualiza o estado interno
     * ghostTop e ghostBottom podem ser null nas bordas globais.
     *
     * @param ghostTop    base do vizinho de cima
     * @param ghostBottom topo do vizinho de baixo
     * @return array de contagem [quantos IGNORANTE, quantos ESPALHADOR, quantos INATIVO].
     */
    @Override
    public synchronized long[] computarGeracao(int[] ghostTop, int[] ghostBottom) {
        int h = banda.length;
        int[][] nova = new int[h][colunas];

        for (int i = 0; i < h; i++) {
            for (int j = 0; j < colunas; j++) {
                int viz = contarVizinhos(i, j, ghostTop, ghostBottom, h);

                nova[i][j] = Grade.proximoEstado(banda[i][j], viz, limiar);
            }
        }

        banda = nova;

        return Grade.contarEstados(banda);
    }

    /**
     * Conta os vizinhos espalhadores de uma célula da banda (vizinhança de Moore,
     * até 8 vizinhos), combinando a banda local com as linhas-fantasma.
     * <p>
     * Quando o vizinho cai acima da banda ({@code i - 1 == -1}) ou abaixo dela
     * ({@code i + 1 == h}), usa-se a ghost row correspondente. Se nao houver ghost
     * row (borda global da matriz), o vizinho nao e contado -- equivalente ao corte
     * de borda da versao sequencial.
     *
     * @param i           indice LOCAL da linha na banda (0 a h-1)
     * @param j           indice da coluna da celula
     * @param ghostTop    ghost row superior (pode ser {@code null} na borda global)
     * @param ghostBottom ghost row inferior (pode ser {@code null} na borda global)
     * @param h           altura da banda (numero de linhas locais)
     * @return numero de vizinhos no estado ESPALHADOR
     */
    private int contarVizinhos(int i, int j, int[] ghostTop, int[] ghostBottom, int h) {
        int total = 0;

        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                if (di == 0 && dj == 0) continue;

                int nj = j + dj;

                if (nj < 0 || nj >= colunas) continue;

                int ni = i + di;
                int valor;

                if (ni == -1) {
                    if (!temTopo) continue; // borda global superior

                    valor = ghostTop[nj];
                } else if (ni == h) {
                    if (!temBase) continue; // borda global inferior

                    valor = ghostBottom[nj];
                } else {
                    valor = banda[ni][nj];
                }

                if (valor == Estados.ESPALHADOR) {
                    total++;
                }
            }
        }

        return total;
    }

    /**
     * Devolve a banda atual.
     * Usado ao final para remontar a grade e verificar.
     *
     * @return A banda atual
     */
    @Override
    public synchronized int[][] coletarBanda() {
        int[][] copia = new int[banda.length][];

        for (int i = 0; i < banda.length; i++) {
            copia[i] = banda[i].clone();
        }

        return copia;
    }

    /**
     * Encerra o processo worker.
     */
    @Override
    public void encerrar() {
        new Thread(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
            }

            System.exit(0);
        }).start();
    }
}
