package test;

public class FlowRate {
    public final double factor;
    public FlowRate(double factor) { this.factor = factor; }
    @Override public String toString() { return factor + "x"; }
}
