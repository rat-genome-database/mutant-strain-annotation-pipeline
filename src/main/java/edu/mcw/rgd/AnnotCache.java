package edu.mcw.rgd;

import edu.mcw.rgd.datamodel.ontology.Annotation;

import java.util.concurrent.ConcurrentHashMap;

public class AnnotCache {

    private ConcurrentHashMap<String, Annotation> _cache = new ConcurrentHashMap<>();

    public void add(Annotation a) {
        String key = computeKey(a);
        _cache.putIfAbsent(key, a);
    }

    public int insertAnnotations(Dao dao) throws Exception {

        for( Annotation a: _cache.values() ) {
            dao.insertAnnotation(a);
        }
        return _cache.size();
    }

    String computeKey(Annotation a) {
        return a.getTermAcc()+"\t"+a.getAnnotatedObjectRgdId()+"\t"+a.getRefRgdId()+"\t"+a.getEvidence()
                +"\t"+a.getWithInfo()+"\t"+a.getQualifier()+"\t"+a.getXrefSource();
    }
}
