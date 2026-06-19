package br.edu.utfpr.grupo7.distribuido;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Processo Worker da simulação distribuída.
 * <p>
 * Cada instância roda em uma JVM própria e registra um WorkerImpl
 * no registro RMI sob o nome "FakeNewsWorker_<id>", ficando
 * disponível para o coordenador.
 *
 * @see WorkerImpl
 * @see GridService
 * @see br.edu.utfpr.grupo7.Distribuido
 */
public class WorkerServer {
    /**
     * Sobe o worker e o registra no RMI.
     * <p>
     * Garante a existência do registro RMI na porta indicada.
     * O primeiro processo a subir cria o registro e os demais o reaproveitam.
     * <p>
     * Como os workers sobem em paralelo, há uma corrida de inicialização, por isso
     * a obtenção do registro é feita com várias tentativas.
     *
     * @param args argumentos de linha de comando:
     *             {@code args[0]} = id do trabalhador (obrigatório);
     *             {@code args[1]} = porta do registro RMI (opcional, padrão 1099)
     * @throws Exception se não for possível obter o registro RMI ou registrar o objeto
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Uso: WorkerServer <id> [porta]");
            System.exit(1);
        }

        int id = Integer.parseInt(args[0]);
        int porta = args.length > 1 ? Integer.parseInt(args[1]) : 1099;

        /*
         Necessário para RMI resolver o host corretamente em ambientes com
         múltiplas interfaces.
        */
        if (System.getProperty("java.rmi.server.hostname") == null) {
            System.setProperty("java.rmi.server.hostname", "127.0.0.1");
        }

        // O primeiro trabalhador a subir CRIA o registro; os demais o reaproveitam.
        Registry registro = null;
        for (int tentativa = 0; tentativa < 50 && registro == null; tentativa++) {
            try {
                registro = LocateRegistry.createRegistry(porta);
            } catch (Exception jaExiste) {
                try {
                    Registry r = LocateRegistry.getRegistry("127.0.0.1", porta);
                    r.list();
                    registro = r;
                } catch (Exception aindaSubindo) {
                    Thread.sleep(150);
                }
            }
        }

        if (registro == null) {
            throw new RuntimeException("Nao foi possivel obter o registro RMI na porta " + porta);
        }

        WorkerImpl worker = new WorkerImpl();
        registro.rebind("FakeNewsWorker_" + id, worker);

        System.out.println("Trabalhador " + id + " pronto na porta " + porta + ".");
    }
}
