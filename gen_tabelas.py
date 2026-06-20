#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Renderiza as tabelas de desempenho como IMAGENS (PNG), no mesmo estilo dos
graficos. Gera tabelas SEPARADAS de speedup e de eficiencia, com as celulas
COLORIDAS conforme o valor (heatmap).

Layout de cada imagem (uma por metrica e por tamanho):
  - linhas   = (geracoes, pct)
  - colunas  = paralelismo por versao  ->  Paralelo p=1, ... | Distribuido p=1, ...

Cores:
  - eficiencia: escala fixa 0..1 (vermelho -> amarelo -> verde);
  - speedup:    divergente centrada em 1,0 (abaixo de 1 = vermelho, 1 = amarelo,
                acima de 1 = verde), com extremos ajustados aos dados.

Saida (em 'resultados/graficos'):
  - tabela_speedup_grade<N>.png
  - tabela_eficiencia_grade<N>.png

Requer: pandas e matplotlib  ->  pip install pandas matplotlib
"""

import os
import sys

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.colors import Normalize, TwoSlopeNorm
import pandas as pd

PARALELAS = ["Paralela", "Distribuida"]
PREFIXO = {"Paralela": "Paralelo", "Distribuida": "Distribuído"}


def fmt_pct(p):
    s = ("%.6f" % float(p)).rstrip("0").rstrip(".") + "%"
    return s if s else "0"


def carregar(caminho):
    df = pd.read_csv(caminho)
    if "geracoes" not in df.columns:
        df["geracoes"] = 0
    if "pct" not in df.columns:
        df["pct"] = 0.0
    return df


def montar_tabela(df, tamanho, metrica):
    """Monta (col_labels, linhas) para a metrica e tamanho dados."""
    cen = df[(df["tamanho"] == tamanho) & (df["versao"].isin(PARALELAS))]
    if cen.empty:
        return None, None

    gers = sorted(cen["geracoes"].unique())
    pcts = sorted(cen["pct"].unique())
    ps = sorted(cen["paralelismo"].unique())
    versoes = [v for v in PARALELAS if v in cen["versao"].unique()]

    valor = cen.groupby(["geracoes", "pct", "versao", "paralelismo"])[metrica].first()

    col_labels = ["Gerações", "Espalhador"]
    for v in versoes:
        for p in ps:
            col_labels.append("%s p=%d" % (PREFIXO[v], int(p)))

    linhas = []
    for g in gers:
        for pc in pcts:
            linha = [str(int(g)), fmt_pct(pc * 100)]
            for v in versoes:
                for p in ps:
                    try:
                        linha.append("%.2f" % float(valor.loc[(g, pc, v, p)]))
                    except KeyError:
                        linha.append("-")
            linhas.append(linha)
    return col_labels, linhas


def escala_cor(metrica, valores, ideal):
    """Define a normalizacao e o colormap conforme a metrica.

    eficiencia -> escala fixa 0..1.
    speedup    -> divergente com ancoras FIXAS: 0 (vermelho), 1,0 (amarelo,
                  neutro = sem ganho) e o speedup ideal/linear (verde). Assim a
                  cor mede o quao perto do ideal o resultado esta, e e
                  comparavel entre tabelas.
    """
    cmap = plt.get_cmap("RdYlGn")
    if metrica == "eficiencia":
        return Normalize(vmin=0.0, vmax=1.0), cmap
    vmax = max(float(ideal), max(valores + [1.0]), 1.01)
    return TwoSlopeNorm(vmin=0.0, vcenter=1.0, vmax=vmax), cmap


def render(col_labels, linhas, destino, metrica, ideal):
    ncols = len(col_labels)
    nrows = len(linhas)
    fig, ax = plt.subplots(figsize=(max(6, ncols * 1.05), max(1.8, (nrows + 1) * 0.42)))
    ax.axis("off")

    tbl = ax.table(cellText=linhas, colLabels=col_labels, cellLoc="center", bbox=[0, 0, 1, 1])
    tbl.auto_set_font_size(False)
    tbl.set_fontsize(9)
    tbl.auto_set_column_width(col=list(range(ncols)))

    # valores numericos das celulas de dados (para a escala de cor)
    numericos = []
    for linha in linhas:
        for cel in linha[2:]:
            try:
                numericos.append(float(cel))
            except ValueError:
                pass
    norm, cmap = escala_cor(metrica, numericos, ideal)

    for (r, c), cell in tbl.get_celld().items():
        cell.set_edgecolor("#cccccc")
        if r == 0:
            cell.set_facecolor("#40466e")
            cell.set_text_props(color="white", fontweight="bold")
        elif c < 2:
            # colunas de rotulo (geracoes, pct)
            cell.set_facecolor("#e8edf3")
        else:
            # celulas de dados: cor conforme o valor
            txt = cell.get_text().get_text()
            try:
                val = float(txt)
            except ValueError:
                cell.set_facecolor("#ffffff")
                continue
            rgba = cmap(norm(val))
            cell.set_facecolor(rgba)
            lum = 0.299 * rgba[0] + 0.587 * rgba[1] + 0.114 * rgba[2]
            cell.set_text_props(color="black" if lum > 0.55 else "white")

    fig.tight_layout()
    fig.savefig(destino, dpi=150, bbox_inches="tight")
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

    metricas = [("speedup", "speedup"), ("eficiencia", "eficiencia")]

    for n in sorted(df["tamanho"].unique()):
        # speedup ideal = maior paralelismo (verde = proximo do linear)
        sub = df[(df["tamanho"] == n) & (df["versao"].isin(PARALELAS))]
        ideal = int(sub["paralelismo"].max()) if not sub.empty else 1
        for col, arq in metricas:
            cols, linhas = montar_tabela(df, n, col)
            if not linhas:
                continue
            destino = os.path.join(saida, "tabela_%s_grade%d.png" % (arq, int(n)))
            gerados.append(render(cols, linhas, destino, col, ideal))

    print("Imagens de tabela geradas em '%s':" % saida)
    for caminho in gerados:
        print("  - " + os.path.basename(caminho))
    print("\nTotal: %d imagem(ns)." % len(gerados))


if __name__ == "__main__":
    main()