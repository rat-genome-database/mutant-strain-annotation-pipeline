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

There are two qualifiers that are restricted to use for strain curation: 'induced' and 'penetrance'.
When the strain has one of the strain-restricted qualifier, only propagate term and evidence code.
There is a full set of MODEL qualifiers used only for strain curation, they should be omitted
when propagate annotations to gene and mutant allele. When the qualifier is omitted, the entry 
in the with also omitted.
  <pre>
  MODEL
  MODEL: age-related
  MODEL: control
  MODEL: diet
  MODEL: disease_progression
  MODEL: induced
  MODEL: onset
  MODEL: spontaneous
  MODEL: treatment
  MODEL: xxx
  </pre>