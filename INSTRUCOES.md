# Instruções de Execução

Simulação de Propagação de Fake News — versões **sequencial**, **paralela** (threads)
e **distribuída** (RMI), com um benchmark que compara as três.

Projeto Maven, pacote `br.edu.utfpr.grupo7`. Todas as classes executáveis ficam em
`src/main/java/br/edu/utfpr/grupo7/`.

---

## 1. Pré-requisitos

- **JDK 8 ou superior** (o projeto compila com `source/target 1.8`).
  - Verifique com `java -version` e `javac -version`.
- **Maven** (opcional, recomendado). Sem Maven, há o caminho alternativo com `javac`.

---

## 2. Compilação

### Com Maven
```bash
mvn compile
```
As classes são geradas em `target/classes`.

### Sem Maven (alternativa)
```bash
# a partir da raiz do projeto
mkdir -p target/classes
javac -d target/classes $(find src/main/java -name "*.java")
```

> Todos os comandos de execução abaixo usam o classpath `target/classes`.

---

## 3. Execução de cada versão

A ordem dos argumentos é posicional; todos têm valor padrão (pode rodar sem argumentos).

### 3.1 Sequencial
```
java -cp target/classes br.edu.utfpr.grupo7.Sequencial <linhas> <colunas> <geracoes> <pct> <limiar> <semente>
```
Padrões: `100 100 50 0.05 3 42`. Exemplo:
```bash
java -cp target/classes br.edu.utfpr.grupo7.Sequencial 1000 1000 40 0.0005 1 42
```

### 3.2 Paralela (threads)
```
java -cp target/classes br.edu.utfpr.grupo7.Paralelo <linhas> <colunas> <geracoes> <pct> <limiar> <nThreads> <semente>
```
Padrões: `100 100 50 0.05 3 <nucleos> 42`. Exemplo (8 threads):
```bash
java -cp target/classes br.edu.utfpr.grupo7.Paralelo 1000 1000 40 0.0005 1 8 42
```

### 3.3 Distribuída (RMI)
A distribuída tem **dois passos**: primeiro suba os *workers*, depois rode o coordenador.
O número de workers no coordenador e a porta devem ser os mesmos usados ao subir os workers.

**Passo 1 — subir N workers** (cada um em um processo; aqui, 4 workers na porta 1099):
```bash
for k in 0 1 2 3; do
  java -cp target/classes br.edu.utfpr.grupo7.distribuido.WorkerServer $k 1099 &
done
```
Cada worker é `WorkerServer <id> [porta]` (id obrigatório, porta padrão 1099). Os ids vão de `0` a `N-1`.

**Passo 2 — rodar o coordenador:**
```
java -cp target/classes br.edu.utfpr.grupo7.Distribuido <linhas> <colunas> <geracoes> <pct> <limiar> <nWorkers> <semente> <porta> <verboso>
```
Padrões: `100 100 50 0.05 3 4 42 1099 true`. Exemplo (4 workers):
```bash
java -cp target/classes br.edu.utfpr.grupo7.Distribuido 1000 1000 40 0.0005 1 4 42 1099 true
```
Ao terminar, o coordenador encerra os workers automaticamente. Para matá-los manualmente:
```bash
pkill -f distribuido.WorkerServer
```

> **No Windows:** abra um terminal por worker (ou use `start`), por exemplo
> `start java -cp target\classes br.edu.utfpr.grupo7.distribuido.WorkerServer 0 1099`.

---

## 4. Benchmark das três versões

O benchmark roda sequencial e paralela no próprio processo e, para a distribuída,
**sobe os workers sozinho** (como processos separados). Basta um comando:

```
java -cp target/classes br.edu.utfpr.grupo7.Benchmark <geracoes> <pct> <limiar> <semente> <reps> <tamanhos> <threads> <workers>
```
Padrões: `40 0.0005 1 42 3 500,1000 1,2,4 1,2,4`. Exemplo:
```bash
java -cp target/classes br.edu.utfpr.grupo7.Benchmark 40 0.0005 1 42 3 500,1000 1,2,4 1,2,4
```
- `<tamanhos>`, `<threads>` e `<workers>` são listas separadas por vírgula (sem espaços).
- Imprime uma tabela com tempo, **speedup**, **eficiência** e a coluna **OK** (verificação
  célula a célula contra a sequencial) e salva `resultados/benchmark_completo.csv`.

> Importante: rode o benchmark com `java -cp target/classes` (como acima). Ele usa o
> classpath atual para lançar os workers; se o classpath estiver errado, os workers não sobem.

---

## 5. Parâmetros (significado)

| Parâmetro | Significado |
|-----------|-------------|
| `linhas`, `colunas` | dimensões da matriz (população) |
| `geracoes` | número máximo de passos de tempo |
| `pct` | percentual inicial de espalhadores (ex.: `0.05` = 5%) |
| `limiar` | nº mínimo de vizinhos espalhadores para um ignorante virar espalhador |
| `nThreads` / `nWorkers` | grau de paralelismo (threads ou processos/máquinas) |
| `semente` | semente do gerador aleatório (reprodutibilidade) |
| `porta` | porta do registro RMI (distribuída) |

> **Dica de cenário:** use `limiar=1` para experimentos de desempenho — gera uma onda
> que se propaga por muitas gerações. Com `limiar` alto e poucos espalhadores, a
> propagação morre em poucas gerações e o tempo fica pequeno demais para medir.

---

## 6. Observações

- **Corretude:** as versões paralela e distribuída são comparadas célula a célula
  contra a sequencial; `0 células diferentes` / `OK=sim` confirma equivalência.
- **Speedup:** só aparece em máquina **multi-core** (paralela) e com **vários
  cores/hosts** (distribuída). Em 1 núcleo, o overhead domina — o que também é um
  resultado válido a relatar.
- **Várias máquinas (distribuída):** suba cada worker no seu host com
  `-Djava.rmi.server.hostname=<IP_DO_HOST>` e ajuste o host de *lookup* no
  `Distribuido` (atualmente fixo em `127.0.0.1`).
- **Saída:** o CSV do benchmark fica em `resultados/benchmark_completo.csv`.
