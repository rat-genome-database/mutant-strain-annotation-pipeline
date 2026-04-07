# mutant-strain-annotation-pipeline

Propagates disease and phenotype annotations from strains to alleles and genes.

## Overview

The pipeline has two annotators:
- **Strain2AlleleAnnotator** — propagates annotations from strains to their associated
  mutant alleles, then to parent genes and orthologs
- **Allele2GeneAnnotator** — propagates annotations from alleles to parent genes and orthologs

Only strains with one associated rat mutant allele are processed.
Strains with multiple associated alleles are skipped.

## Propagation rules

**Disease annotations (aspect D)** on a strain propagate to:
1. Associated mutant allele (same evidence: IMP or IAGP)
2. Parent gene of the allele (same evidence)
3. Orthologs of the parent gene for other species (ISO)

**MP annotations (aspect N)** on a strain propagate to:
1. Associated mutant allele (same evidence: IMP or IAGP)
2. Parent gene of the allele (same evidence)

## Qualifier handling

Strain-restricted qualifiers (`induced`, `penetrance`): only term and evidence code are propagated.

MODEL qualifiers are omitted when propagating to genes and alleles.
When a qualifier is omitted, the WITH field is also omitted.
```
MODEL, MODEL: age-related, MODEL: control, MODEL: diet,
MODEL: disease_progression, MODEL: induced, MODEL: onset,
MODEL: spontaneous, MODEL: treatment, MODEL: xxx
```

## Build and run

Requires Java 17. Built with Gradle:
```
./gradlew clean assembleDist
```