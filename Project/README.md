# MPC-ZMD — Digital Watermarking Toolkit

JavaFX application for embedding and extracting digital watermarks in images, with automated robustness testing against common attacks.

## Watermarking Methods

- **LSB** — Least Significant Bit substitution (spatial domain, fragile)
- **DCT** — Discrete Cosine Transform domain embedding (block-based, semi-robust)
- **Spread Spectrum** — Additive spread spectrum in spatial domain (Cox et al. 1997)
- **Patchwork** — Statistical patchwork method (Bender et al. 1996)

## Attacks

JPEG compression (10–90%), PNG re-compression, rotation (45°/90°), resize (50%/75%), mirror, crop (10%).

## Pre-generated Report

A complete Excel report with all 4 methods × 3 parameter configs × 13 attacks (156 scenarios total) with embedded images is available at:

**[`watermark-report-all.xlsx`](watermark-report-all.xlsx)**

Each sheet (LSB, DCT, Spread Spectrum, Patchwork) shows how different embedding strengths survive each attack.

## Running

### Prerequisites

- **Java SDK 25+**, **Apache Maven** on `PATH`

### Run the app

```powershell
mvn javafx:run
```

### Run tests (generates xlsx report in `target/`)

```powershell
mvn test -Dtest=tests.ExportPipelineTest
```

### VS Code

Open the project folder, install [Extension Pack for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack), press **F5**.
