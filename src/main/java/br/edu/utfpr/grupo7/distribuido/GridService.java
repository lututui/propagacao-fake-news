package br.edu.utfpr.grupo7.distribuido;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * RMI de um worker.
 * <p>
 * Cada worker e responsável por uma FAIXA DE LINHAS da matriz.
 * O coordenador orquestra as gerações e a troca de linhas-fantasma
 * (ghost rows) nas fronteiras entre bandas vizinhas.
 * <p>
 * Geração
 * A simulação avança no tempo em passos discretos, cada passo é uma
 * "geração". Em cada geração, toda a matriz é atualizada de uma vez.
 * Cada célula olha seus vizinhos na geração anterior e decide seu
 * próximo estado. Todas as células leem do estado anterior e escrevem
 * o novo estado simultaneamente, ninguém enxerga uma atualização
 * parcial.
 * <p>
 * Linha-fantasma (ghost row)
 * A matriz é fatiada em bandas horizontais, uma por worker.
 * Para calcular uma célula, você precisa dos vizinhos dela, mas as
 * linhas na borda da banda têm vizinhos que pertencem à banda do
 * worker vizinho. Cada worker precisa de uma cópia somente-leitura
 * da linha de fronteira dos vizinhos. Essas linhas emprestadas são
 * as ghost rows: o worker não as atualiza, só as usa para conseguir
 * calcular corretamente suas próprias linhas de borda.
 * <p>
 * Worker 0 → linhas 0, 1, 2
 * Worker 1 → linhas 3, 4, 5
 * Worker 2 → linhas 6, 7, 8
 * <p>
 * Visão do Worker 1 ao computar sua banda:
 * <p>
 * [ linha 2 ] ← ghost TOP (cópia da última linha do Worker 0)
 * [ linha 3 ] ┐
 * [ linha 4 ] ├ banda própria do Worker 1
 * [ linha 5 ] ┘
 * [ linha 6 ] ← ghost BOTTOM (cópia da primeira linha do Worker 2)
 * <p>
 * Como o estado muda a cada geração, essas fronteiras precisam ser
 * trocadas a cada geração, feito no coordenador.
 */
public interface GridService extends Remote {

    /**
     * Configura a banda inicial deste worker.
     *
     * @param banda              sub-matriz (h x colunas)
     * @param linhaGlobalInicial índice global da primeira linha da banda
     * @param totalLinhas        numero total de linhas da matriz global
     * @param limiar             limiar de convencimento da regra de transição
     */
    void configurar(int[][] banda, int linhaGlobalInicial, int totalLinhas, int limiar)
            throws RemoteException;

    /**
     * Primeira linha (topo) da banda no estado ATUAL
     *
     * @return Ghost row inferior para vizinho de cima.
     */
    int[] linhaSuperior() throws RemoteException;

    /**
     * Última linha (base) da banda no estado ATUAL
     *
     * @return Ghost row superior para vizinho de baixo.
     */
    int[] linhaInferior() throws RemoteException;

    /**
     * Calcula a proxima geracao desta banda. Atualiza o estado interno
     * ghostTop e ghostBottom podem ser null nas bordas globais.
     *
     * @param ghostTop    base do vizinho de cima
     * @param ghostBottom topo do vizinho de baixo
     *
     * @return array de contagem [quantos IGNORANTE, quantos ESPALHADOR, quantos INATIVO].
     */
    long[] computarGeracao(int[] ghostTop, int[] ghostBottom) throws RemoteException;

    /**
     * Devolve a banda atual.
     * Usado ao final para remontar a grade e verificar.
     *
     * @return A banda atual
     */
    int[][] coletarBanda() throws RemoteException;

    /**
     * Encerra o processo worker.
     */
    void encerrar() throws RemoteException;
}