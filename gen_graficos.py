#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Gera graficos de comparacao de desempenho a partir do CSV de saida do Benchmark.

Le 'resultados/benchmark_completo.csv' (colunas: geracoes, pct, tamanho, versao,
paralelismo, tempo_s, speedup, eficiencia, correto) e, para evitar dezenas de
arquivos, COMBINA os cenarios em poucas figuras usando paineis (small multiples):
as linhas dos paineis sao as 'geracoes' e as colunas sao os 'pct'.

Figuras geradas:
  1. speedup_grade<N>.png      -> 1 figura por tamanho; cada painel: speedup    x paralelismo
  2. eficiencia_grade<N>.png   -> 1 figura por tamanho; cada painel: eficiencia x paralelismo
  3. tempo_x_tamanho.png       -> 1 figura;             cada painel: tempo      x tamanho (3 versoes)

Exemplo: para geracoes={5,50,100}, pct={0.0005,0.005,0.05} e tamanhos={500,1000}
sao geradas 5 figuras (2 + 2 + 1) em vez de 45 imagens separadas.

Uso:
    python plot_benchmark.py [caminho_do_csv] [pasta_de_saida]

Padroes: resultados/benchmark_completo.csv  e  resultados/graficos
Requer: pandas e matplotlib  ->  pip install pandas matplotlib
"""

import os
import sys

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import pandas as pd

PARALELAS = ["Paralela", "Distribuida"]


def fmt_pct(p):
    s = ("%.6f" % float(p)).rstrip("0").rstrip(".")
    return s if s else "0"


def carregar(caminho):
    df = pd.read_csv(caminho)
    if "geracoes" not in df.columns:
        df["geracoes"] = 0
    if "pct" not in df.columns:
        df["pct"] = 0.0
    return df


def _legenda_unica(fig, axes):
    """Monta uma unica legenda para a figura, sem rotulos repetidos."""
    pares = {}
    for linha in axes:
        for ax in linha:
            for h, l in zip(*ax.get_legend_handles_labels()):
                pares.setdefault(l, h)
    if pares:
        fig.legend(pares.values(), pares.keys(),
                   loc="upper center", bbox_to_anchor=(0.5, 0.965),
                   ncol=min(len(pares), 4))


def _grade(df):
    """Valores ordenados de geracoes (linhas) e pct (colunas) dos paineis."""
    return sorted(df["geracoes"].unique()), sorted(df["pct"].unique())


def figura_metrica(df, tamanho, metrica, ylabel, titulo, destino):
    """Figura facetada (geracoes x pct) de 'speedup' ou 'eficiencia' x paralelismo."""
    gers, pcts = _grade(df)
    fig, axes = plt.subplots(len(gers), len(pcts),
                             figsize=(max(4, 3.6 * len(pcts)), max(3, 2.8 * len(gers))),
                             squeeze=False, sharex=True, sharey=True)

    for i, g in enumerate(gers):
        for j, p in enumerate(pcts):
            ax = axes[i][j]
            cen = df[(df["tamanho"] == tamanho) & (df["geracoes"] == g) & (df["pct"] == p)]
            paral = cen[cen["versao"].isin(PARALELAS)]

            for versao, grupo in paral.groupby("versao"):
                grupo = grupo.sort_values("paralelismo")
                ax.plot(grupo["paralelismo"], grupo[metrica], marker="o", label=versao)

            #if not paral.empty:
            #    if metrica == "speedup":
             #       pmax = int(paral["paralelismo"].max())
                    #ax.plot(range(1, pmax + 1), range(1, pmax + 1),
                           # linestyle="--", color="gray", label="Ideal")
             #   else:
             #       ax.axhline(1.0, linestyle="--", color="gray", label="Ideal")

            ax.set_title("geracoes=%d  pct=%s" % (int(g), fmt_pct(p)), fontsize=9)
            ax.grid(True, linestyle=":", alpha=0.5)
            if i == len(gers) - 1:
                ax.set_xlabel("threads / workers")
            if j == 0:
                ax.set_ylabel(ylabel)

    _legenda_unica(fig, axes)
    #fig.suptitle(titulo, y=0.995)
    fig.tight_layout(rect=[0, 0, 1, 0.92])
    fig.savefig(destino, dpi=120)
    plt.close(fig)
    return destino


def figura_tempo_x_tamanho(df, destino):
    """Figura facetada (geracoes x pct): tempo x tamanho comparando as 3 versoes."""
    if df["tamanho"].nunique() < 2:
        return None  # so faz sentido com varios tamanhos

    gers, pcts = _grade(df)
    fig, axes = plt.subplots(len(gers), len(pcts),
                             figsize=(max(4, 3.6 * len(pcts)), max(3, 2.8 * len(gers))),
                             squeeze=False, sharex=True)

    for i, g in enumerate(gers):
        for j, p in enumerate(pcts):
            ax = axes[i][j]
            gp = df[(df["geracoes"] == g) & (df["pct"] == p)]

            seq = gp[gp["versao"] == "Sequencial"].sort_values("tamanho")
            if not seq.empty:
                ax.plot(seq["tamanho"], seq["tempo_s"], marker="o", label="Sequencial")

            for versao in PARALELAS:
                sub = gp[gp["versao"] == versao]
                if sub.empty:
                    continue
                pmax = sub["paralelismo"].max()
                melhor = sub[sub["paralelismo"] == pmax].sort_values("tamanho")
                ax.plot(melhor["tamanho"], melhor["tempo_s"],
                        marker="o", label=versao + " (melhor)")

            ax.set_title("geracoes=%d  pct=%s" % (int(g), fmt_pct(p)), fontsize=9)
            ax.grid(True, linestyle=":", alpha=0.5)
            if i == len(gers) - 1:
                ax.set_xlabel("tamanho (N)")
            if j == 0:
                ax.set_ylabel("tempo (s)")

    _legenda_unica(fig, axes)
    #fig.suptitle("Tempo x tamanho da matriz (Paralela/Distribuida no melhor caso)", y=0.995)
    fig.tight_layout(rect=[0, 0, 1, 0.92])
    fig.savefig(destino, dpi=120)
    plt.close(fig)
    return destino


def main():
    csv = os.path.join("resultados", "benchmark_completo.csv")
    saida = os.path.join("resultados", "graficos")

    if not os.path.isfile(csv):
        print("Arquivo nao encontrado: %s" % csv)
        sys.exit(1)

    os.makedirs(saida, exist_ok=True)
    df = carregar(csv)
    gerados = []

    # 1 e 2: uma figura de speedup e uma de eficiencia POR TAMANHO
    for n in sorted(df["tamanho"].unique()):
        gerados.append(figura_metrica(
            df, n, "speedup", "speedup",
            "Speedup x paralelismo - grade %dx%d" % (int(n), int(n)),
            os.path.join(saida, "speedup_grade%d.png" % int(n))))
        gerados.append(figura_metrica(
            df, n, "eficiencia", "eficiencia",
            "Eficiencia x paralelismo - grade %dx%d" % (int(n), int(n)),
            os.path.join(saida, "eficiencia_grade%d.png" % int(n))))

    # 3: uma unica figura de tempo x tamanho
    d = figura_tempo_x_tamanho(df, os.path.join(saida, "tempo_x_tamanho.png"))
    if d:
        gerados.append(d)

    gerados = [g for g in gerados if g]
    print("Figuras geradas em '%s':" % saida)
    for caminho in gerados:
        print("  - " + os.path.basename(caminho))
    print("\nTotal: %d figura(s)." % len(gerados))


if __name__ == "__main__":
    main()