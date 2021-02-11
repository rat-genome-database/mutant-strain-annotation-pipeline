package edu.mcw.rgd;

import edu.mcw.rgd.datamodel.*;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.process.CounterPool;
import edu.mcw.rgd.process.Utils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author mtutaj
 * @since Dec 12, 2017
 */
public class Main {

    Logger log = Logger.getLogger("status");

    private Dao dao;
    private int createdBy;
    private String version;
    private Set<String> strainRestrictedQualifiers;

    // species to make orthologous gene disease annotations
    private Set<Integer> allowedSpeciesTypes = new HashSet<>();

    public Main() {
        allowedSpeciesTypes.add(SpeciesType.HUMAN);
        allowedSpeciesTypes.add(SpeciesType.MOUSE);
    }

    public static void main(String[] args) throws Exception {

        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(bf).loadBeanDefinitions(new FileSystemResource("properties/AppConfigure.xml"));

        Main loader = (Main) (bf.getBean("main"));
        try {
            loader.run();
        } catch (Exception e) {
            Utils.printStackTrace(e, loader.log);
            throw e;
        }
    }
    
    void run() throws Exception {
        Date dateStart = new Date();
        log.info(getVersion());

        SimpleDateFormat sdt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        log.info("  started at: "+sdt.format(dateStart));
        log.info("  "+dao.getConnectionInfo());
        log.info("===");

        run("D");
        run("N");

        log.info("=== DONE ===  elapsed: " + Utils.formatElapsedTime(dateStart.getTime(), System.currentTimeMillis()));
        log.info("");
    }

    void run(String aspect) throws Exception {

        CounterPool counters = new CounterPool();

        List<Annotation> baseAnnots = getDao().getBaseAnnotations(aspect);
        counters.add("  base annotations for ontology with aspect "+aspect, baseAnnots.size());

        AnnotCache inRgdAnnots = new AnnotCache();
        int initAnnotCount = inRgdAnnots.loadFromDb(getCreatedBy(), aspect, getDao());
        counters.add("IN RGD INITIAL ANNOTATION COUNT", initAnnotCount);


        List<Annotation> alleleAnnots = new ArrayList<>();
        List<Annotation> geneAnnots = new ArrayList<>();
        List<Annotation> orthologGeneAnnots = new ArrayList<>();

        Collections.shuffle(baseAnnots);
        baseAnnots.parallelStream().forEach( a -> {
            List<Strain2MarkerAssociation> geneAlleles;

            try {
                geneAlleles = getDao().getGeneAllelesForStrain(a.getAnnotatedObjectRgdId());
                if( geneAlleles.isEmpty() ) {
                    counters.increment("  base annotations without gene alleles");
                } else if( geneAlleles.size()==1 ) {
                    counters.increment("  base annotations with one gene allele");

                    int alleleRgdId = geneAlleles.get(0).getDetailRgdId();
                    Gene allele = getDao().getGene(alleleRgdId);
                    if( !allele.isVariant() ) {
                        log.warn("WARNING! "+a.getObjectSymbol()+" RGD:"+a.getAnnotatedObjectRgdId()+"  is associated with a gene: "+allele.getSymbol()+" RGD:"+allele.getRgdId());
                        return;
                    }

                    Annotation alleleAnn = qcGeneAllele(a, geneAlleles.get(0));
                    synchronized (alleleAnnots) {
                        alleleAnnots.add(alleleAnn);
                    }

                    Gene gene = geneFromAllele(alleleAnn);
                    if( gene==null ) {
                        return; // unexpected
                    }
                    Annotation geneAnn = qcGene(gene, alleleAnn);
                    synchronized (geneAnnots) {
                        geneAnnots.add(geneAnn);
                    }

                    List<Annotation> oAnnots = qcOrthologAnnots(geneAnn);
                    synchronized (orthologGeneAnnots) {
                        orthologGeneAnnots.addAll(oAnnots);
                    }
                } else {
                    counters.increment("  base annotations with multiple gene alleles");
                }
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
        });

        inRgdAnnots.qcAndLoad(alleleAnnots, " gene allele annotations", counters, getDao());
        inRgdAnnots.qcAndLoad(geneAnnots, " gene annotations", counters, getDao());
        inRgdAnnots.qcAndLoad(orthologGeneAnnots, " ortholog gene annotations", counters, getDao());

        int deleted = inRgdAnnots.deleteOrphanedAnnotations(getDao());
        counters.add(" total annotations deleted", deleted);

        counters.add("FINAL ANNOTATION COUNT ", inRgdAnnots.size());

        log.info(counters.dumpAlphabetically());
    }

    Annotation qcGeneAllele(Annotation strainAnn, Strain2MarkerAssociation assoc) throws Exception {

        int geneAlleleRgdId = assoc.getDetailRgdId();

        // create incoming gene allele annotation
        Annotation alleleAnn = createDerivedAnnotation(strainAnn, geneAlleleRgdId, null);
        return alleleAnn;
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

            List<Ortholog> orthologs = getDao().getOrthologsForSourceRgdId(geneAnnot.getAnnotatedObjectRgdId(), allowedSpeciesTypes);
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

        // RULE: if an annotation has a strain-restricted qualifier, QUALIFIER and WITH_INFO must not be propagated
        if( a.getQualifier()!=null &&
                ( getStrainRestrictedQualifiers().contains(a.getQualifier())
                        || a.getQualifier().startsWith("MODEL") )
        ) {
            derivedAnn.setQualifier(null);
            derivedAnn.setWithInfo(null);
        }

        if( evidenceCodeOverride!=null ) {
            derivedAnn.setEvidence(evidenceCodeOverride);
        }

        if( derivedAnn.getEvidence().equals("ISO") ) {
            // ISO annots must have WITH_INFO field set
            String withInfo = "RGD:"+a.getAnnotatedObjectRgdId();
            if( derivedAnn.getWithInfo() == null ) {
                derivedAnn.setWithInfo(withInfo);
            } else {
                derivedAnn.setWithInfo(derivedAnn.getWithInfo()+"|"+withInfo);
            }
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

    public void setDao(Dao dao) {
        this.dao = dao;
    }

    public Dao getDao() {
        return dao;
    }

    public void setCreatedBy(int createdBy) {
        this.createdBy = createdBy;
    }

    public int getCreatedBy() {
        return createdBy;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public void setStrainRestrictedQualifiers(Set<String> strainRestrictedQualifiers) {
        this.strainRestrictedQualifiers = strainRestrictedQualifiers;
    }

    public Set<String> getStrainRestrictedQualifiers() {
        return strainRestrictedQualifiers;
    }
}

