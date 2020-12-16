package edu.mcw.rgd;

import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.process.CounterPool;
import edu.mcw.rgd.process.Utils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AnnotCache {

    private Date cutoffDate = new Date();

    private HashMap<String, Annotation> inRgdAnnots = new HashMap<>();

    public int size() {
        return inRgdAnnots.size();
    }

    public int loadFromDb(int createdBy, String aspect, Dao dao) throws Exception {
        List<Annotation> annots = dao.getAnnotationsModifiedBeforeTimestamp(createdBy, cutoffDate, aspect);
        for( Annotation a: annots ) {
            String key = AnnotCache.computeKey(a);
            inRgdAnnots.put(key, a);
        }
        return annots.size();
    }

    public Annotation getAnnotation(Annotation a) {
        String key = AnnotCache.computeKey(a);
        Annotation annotInRgd = inRgdAnnots.get(key);
        return annotInRgd;
    }

    public void qcAndLoad(List<Annotation> incomingAnnots, String counterPrefix, CounterPool counters, Dao dao) throws Exception {

        for( Annotation a: incomingAnnots ) {

            String key = AnnotCache.computeKey(a);
            Annotation annotInRgd = inRgdAnnots.get(key);
            if( annotInRgd==null ) {

                // see if there is a similar annotation not created by the pipeline
                int annotKey = dao.getAnnotationKey(a);
                if( annotKey!=0 ) {
                    counters.increment(counterPrefix+" up-to-date");
                } else {
                    dao.insertAnnotation(a);
                    counters.increment(counterPrefix + " inserted");
                    inRgdAnnots.put(key, a);
                }
                continue;
            }

            // check if you need to update notes, annot ext
            boolean changed = !Utils.stringsAreEqual(annotInRgd.getNotes(), a.getNotes())
                    || !Utils.stringsAreEqual(annotInRgd.getAnnotationExtension(), a.getAnnotationExtension())
                    || !Utils.stringsAreEqual(annotInRgd.getGeneProductFormId(), a.getGeneProductFormId());

            if( changed ) {
                dao.updateAnnotation(a, annotInRgd);
                counters.increment(counterPrefix+" updated");
            } else {
                dao.updateLastModified(a.getKey());
                counters.increment(counterPrefix+" up-to-date");
            }
            annotInRgd.setLastModifiedDate(new Date());
        }
    }

    public int deleteOrphanedAnnotations(Dao dao) throws Exception {
        int deleted = 0;
        Iterator<Map.Entry<String, Annotation>> it = inRgdAnnots.entrySet().iterator();
        while( it.hasNext() ) {
            Map.Entry<String, Annotation> entry = it.next();
            Annotation a = entry.getValue();
            if( a.getLastModifiedDate().compareTo(cutoffDate)<0 ) {
                dao.deleteAnnotation(a);
                it.remove();
                deleted++;
            }
        }
        return deleted;
    }

    static public String computeKey(Annotation a) {
        return a.getTermAcc()+"\t"+a.getAnnotatedObjectRgdId()+"\t"+a.getRefRgdId()+"\t"+a.getEvidence()
                +"\t"+a.getWithInfo()+"\t"+a.getQualifier()+"\t"+a.getXrefSource();
    }
}
