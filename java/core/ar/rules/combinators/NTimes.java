package ar.rules.combinators;

import ar.Aggregates;
import ar.Resources;
import ar.Transfer;
import ar.Renderer;
import ar.util.CacheProvider;

/** Apply an operator N times.*/
public class NTimes<IN> implements Transfer<IN,IN> {
    protected final Transfer<IN,IN> base;
    protected final int n;
    protected final Renderer renderer;

    public NTimes(int n, Transfer<IN,IN> base) {
        this(Resources.DEFAULT_RENDERER, n, base);
    }

    public NTimes(Renderer renderer, int n, Transfer<IN,IN> base) {
        this.renderer=renderer;
        this.base=base;
        this.n = n;
    }
    public IN emptyValue() {return base.emptyValue();}
    public Specialized<IN> specialize(Aggregates<? extends IN> aggregates) {
        return new Specialized<>(renderer, base, n, aggregates);
    }

    public static class Specialized<IN> extends NTimes<IN> implements Transfer.Specialized<IN,IN>, CacheProvider.CacheTarget<IN,IN> {
        protected final CacheProvider<IN,IN> cache;
        protected final Transfer.Specialized<IN,IN> op;


        public Specialized(Renderer renderer, Transfer<IN,IN> base, int n, Aggregates<? extends IN> aggs) {
            super(renderer, n, base);
            this.cache = new CacheProvider<>(this);
            op = base.specialize(aggs);
        }

        public Aggregates<? extends IN> build(Aggregates<? extends IN> aggs) {
            for (int i=0; i<n; i++){aggs = renderer.transfer(aggs, op);}
            return aggs;
        }

        @Override
        public IN at(int x, int y, Aggregates<? extends IN> aggregates) {
            return cache.get(aggregates).get(x,y);
        }
    }
}