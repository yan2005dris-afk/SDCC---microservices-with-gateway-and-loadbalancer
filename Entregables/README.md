# Entregables

- [Informe experimental P01–P08 en PDF](pdf/informe.pdf)
- [Código fuente LaTeX](pdf/informe.tex)
- [Informe Markdown](../docs/plan/INFORME_P01_P08.md)
- [Guion del video](../docs/plan/GUION_VIDEO.md)

## Compilar el PDF

Desde `Entregables/pdf/`, ejecutar dos veces XeLaTeX para actualizar el índice. Mantener `UPSELogo.png` junto a `informe.tex`.

```bash
xelatex -halt-on-error -interaction=nonstopmode informe.tex
xelatex -halt-on-error -interaction=nonstopmode informe.tex
```

El informe PDF enlaza las evidencias publicadas en el commit evaluado de documentación. Los archivos de evidencia también están disponibles en [docs/evidencias](../docs/evidencias/README.md).
