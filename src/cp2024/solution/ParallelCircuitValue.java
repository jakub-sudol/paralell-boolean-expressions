package cp2024.solution;

import cp2024.circuit.CircuitValue;

import java.util.concurrent.LinkedBlockingQueue;

public class ParallelCircuitValue implements CircuitValue {

    private int value; // -1,0,1; -1 means interrupted exception
    private boolean calculated;
    private final LinkedBlockingQueue<Integer> queue;

    public ParallelCircuitValue(){
        calculated = false;
        queue = new LinkedBlockingQueue<>();
    }

    public LinkedBlockingQueue<Integer> getQueue() {
        return queue;
    }

    @Override
    public boolean getValue() throws InterruptedException {
        if(!calculated) {
            value = queue.take(); // waiting for the result
            calculated = true;
        }
        if(value < 0) { // if taken interrupted exception
            throw new InterruptedException();
        }else{
            return (value == 1);
        }
    }
}
