# mutant-strain-annotation-pipeline
****Propagate disease and phenotype annotations from strains to alleles and genes.

The pipeline only propagate strains with one associated  rat mutant allele.  
Do not propagate when strains have multiple associated alleles.

Disease annotation to strain, propagate to:
1. associated mutant allele (IMP, sometimes IAGP, same evidence with the strain annotation)
2. the parent gene of the allele (IMP, sometimes IAGP, same evidence with the strain annotation)
3. the orthologs for other species  of the parent gene (ISO)

MP annotation to strain, propagate to:
1. associated mutant allele (IMP, sometimes IAGP, same evidence with the strain annotation)
2. the parent gene of the allele (IMP, sometimes IAGP, same evidence with the strain annotation)
