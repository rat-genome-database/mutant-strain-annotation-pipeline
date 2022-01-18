package edu.mcw.rgd;

import edu.mcw.rgd.datamodel.Gene;
import edu.mcw.rgd.datamodel.Ortholog;
import edu.mcw.rgd.datamodel.RgdId;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.process.CounterPool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Propagate annotations from mutant allele to parent gene.
 * There are some disease/phenotype annotations that cannot be curated to strains, but they are good to curated to the mutant allele.
 * Propagate these type of annotations to the parent genes.  Examples are some cellular phenotypes of mutant cells,
 * they might not be appropriate to annotate the strain, but is good for gene and allele annotation.
 * Should be run after the mutant strain pipeline. The mutant pipeline will fill in the strain-allele-parent annotations
 * and then run this new pipeline to see if there are annotations to the mutant allele, but not to its parent gene.
 */
public class Allele2GeneAnnotator extends BaseAnnotator {

    private int createdBy; // unique pipeline id

    Logger log = LogManager.getLogger("status");


    public void run() throws Exception {
        log.info("===");
        log.info("===");
        log.info("  allele2gene annotator started");

        run("D");
        run("N");
    }

    void run(String aspect) throws Exception {

        CounterPool counters = new CounterPool();

        List<Annotation> baseAnnots = getDao().getBaseAnnotationsForAlleles(aspect);
        counters.add("  allele base annotations for ontology with aspect "+aspect, baseAnnots.size());

        AnnotCache inRgdAnnots = new AnnotCache();
        int initAnnotCount = inRgdAnnots.loadFromDb(getCreatedBy(), aspect, getDao());
        counters.add("IN RGD INITIAL ANNOTATION COUNT", initAnnotCount);


        List<Annotation> geneAnnots = new ArrayList<>();
        List<Annotation> orthologGeneAnnots = new ArrayList<>();

        Collections.shuffle(baseAnnots);
        baseAnnots.parallelStream().forEach( a -> {

            try {

                Gene allele = getDao().getGene(a.getAnnotatedObjectRgdId());
                if( !allele.isVariant() ) {
                    throw new Exception("LOGIC ERROR: gene is not an allele!");
                }

                Gene gene = geneFromAllele(a);
                if( gene==null ) {
                    return; // unexpected
                }
                Annotation geneAnn = qcGene(gene, a);
                synchronized (geneAnnots) {
                    geneAnnots.add(geneAnn);
                }

                List<Annotation> oAnnots = qcOrthologAnnots(geneAnn);
                synchronized (orthologGeneAnnots) {
                    orthologGeneAnnots.addAll(oAnnots);
                }
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
        });

        inRgdAnnots.qcAndLoad(geneAnnots, " gene annotations", counters, getDao());
        inRgdAnnots.qcAndLoad(orthologGeneAnnots, " ortholog gene annotations", counters, getDao());

        int deleted = inRgdAnnots.deleteOrphanedAnnotations(getDao());
        counters.add(" total annotations deleted", deleted);

        counters.add("FINAL ANNOTATION COUNT ", inRgdAnnots.size());

        log.info(counters.dumpAlphabetically());
    }

    Gene geneFromAllele(Annotation alleleAnn) throws Exception {

        List<Gene> genes = getDao().getGeneFromAllele(alleleAnn.getAnnotatedObjectRgdId());
        if (genes.isEmpty()) {
            log.warn("Allele " + alleleAnn.getObjectSymbol() + " RGD:" + alleleAnn.getAnnotatedObjectRgdId() + " does NOT have a parent gene associated!");
            return null;
        }
        if (genes.size() != 1) {
            log.warn("Allele " + alleleAnn.getObjectSymbol() + " RGD:" + alleleAnn.getAnnotatedObjectRgdId() + " has multiple parent genes associated!");
            return null;
        }
        return genes.get(0);
    }

    Annotation qcGene(Gene gene, Annotation base) throws Exception {

        // create incoming gene annotation
        return createDerivedAnnotation(base, gene.getRgdId(), null);
    }

    List<Annotation> qcOrthologAnnots(Annotation geneAnnot ) throws Exception {

        List<Annotation> annots = new ArrayList<>();

        // ortholog annotations create only for disease terms
        if( geneAnnot.getAspect().equals("D") ) {

            List<Ortholog> orthologs = getDao().getOrthologsForSourceRgdId(geneAnnot.getAnnotatedObjectRgdId(), getAllowedSpeciesTypes());
            for( Ortholog o: orthologs ) {
                Annotation a = createDerivedAnnotation(geneAnnot, o.getDestRgdId(), "ISO");
                annots.add(a);
            }
        }
        return annots;
    }

    Annotation createDerivedAnnotation(Annotation a, int derivedRgdId, String evidenceCodeOverride) throws Exception {

        // create derived annotation
        Annotation derivedAnn = (Annotation) a.clone();
        derivedAnn.setKey(0);
        derivedAnn.setAnnotatedObjectRgdId(derivedRgdId);

        if( evidenceCodeOverride!=null ) {
            derivedAnn.setEvidence(evidenceCodeOverride);
        }

        if( derivedAnn.getEvidence().equals("ISO") ) {
            // ISO annots must have WITH_INFO field set to RGD ID of source annotation
            String withInfo = "RGD:"+a.getAnnotatedObjectRgdId();
            derivedAnn.setWithInfo(withInfo);
        }

        // set up properly rgd-id related fields
        Gene gene = getDao().getGene(derivedRgdId);
        derivedAnn.setRgdObjectKey(RgdId.OBJECT_KEY_GENES);
        derivedAnn.setObjectName(gene.getName());
        derivedAnn.setObjectSymbol(gene.getSymbol());

        derivedAnn.setCreatedBy(getCreatedBy());
        derivedAnn.setCreatedDate(new Date());
        derivedAnn.setLastModifiedBy(getCreatedBy());
        derivedAnn.setLastModifiedDate(new Date());
        return derivedAnn;
    }





    public int getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(int createdBy) {
        this.createdBy = createdBy;
    }
}
