package cp2024.solution;

import cp2024.circuit.*;
import cp2024.demo.BrokenCircuitValue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

public class ParallelCircuitSolver implements CircuitSolver {

    private Boolean acceptComputations = true;
    private final Map<CircuitValue,Thread> threads;

    private static class Worker implements Runnable {

        // the worker waits for sons' results on his queue 
        // and then pushes the result to the father by father's queue

        private final CircuitNode node;
        private final LinkedBlockingQueue <Integer> myQueue;
        private final LinkedBlockingQueue <Integer> fathersQueue;
        private boolean result;
        private Thread[] threads; // threads (sons) created by this worker

        public Worker(CircuitNode node, LinkedBlockingQueue <Integer> fathersQueue){
            this.node = node;
            this.fathersQueue = fathersQueue;
            this.myQueue = new LinkedBlockingQueue<>();
        }

        public boolean getResult() {
            return result;
        }

        @Override
        public void run() {
            try {
                this.result = recursive(node);
                Integer res = result ? 1 : 0;
                fathersQueue.put(res); // sending the result to the father
            } catch (InterruptedException e) {
                Integer res = -1; // sending interrupted exception to the father
                try {
                    interruptThreads();
                    fathersQueue.put(res);
                } catch (InterruptedException ex) {}
            }
        }

        private boolean recursive(CircuitNode node)
                throws InterruptedException {
            if (node.getType() == NodeType.LEAF)
                return ((LeafNode) node).getValue();

            CircuitNode[] args = node.getArgs();

            return switch (node.getType()) {
                case IF -> solveIF(args);
                case AND -> solveAND(args);
                case OR -> solveOR(args);
                case GT -> solveGT(args, ((ThresholdNode) node).getThreshold());
                case LT -> solveLT(args, ((ThresholdNode) node).getThreshold());
                case NOT -> solveNOT(args);
                default -> throw new RuntimeException("Illegal type " + node.getType());
            };
        }

        // creates workers and threads, starts threads (sons)
        private void prepareThreads(CircuitNode[] args) {
            Thread[] threads = new Thread[args.length];
            Worker[] workers = new Worker[args.length];
            for (int i = 0; i < args.length; i++) {
                workers[i] = new Worker(args[i], myQueue);
                threads[i] = new Thread(workers[i]);
            }

            for (Thread thread : threads) {
                thread.start();
            }

            this.threads = threads;
        }

        // interrupts threads (sons)
        private void interruptThreads() throws InterruptedException {
            if(threads != null) {
                for (Thread thread : threads) {
                    thread.interrupt();
                }
                try {
                    for (Thread thread : threads) {
                        thread.join();
                    }
                } catch (InterruptedException e) {}
            }
        }

        private boolean solveIF(CircuitNode[] args)
                throws InterruptedException {
            Worker[] workers = new Worker[args.length];
            Thread[] threads = new Thread[args.length];
            for (int i = 0; i < args.length; i++) {
                workers[i] = new Worker(args[i], myQueue);
                threads[i] = new Thread(workers[i]);
            }

            for (Thread thread : threads) {
                thread.start();
            }

            int[] results = new int[args.length]; // -1, 0 or 1
            Arrays.fill(results, -1); // -1 means "not calculated"

            int counter = 0;
            boolean result = true;

            while(counter < args.length) {
                myQueue.take(); //
                for(int i = 0; i < args.length; i++) {
                    // getting calculated results
                    if (!threads[i].isAlive()) {
                        results[i] = workers[i].getResult() ? 1 : 0;
                    }
                }
                    // checking whether the result is known

                    if(results[0] == 1){
                        threads[2].interrupt();
                        while(threads[1].isAlive()){
                            myQueue.take();
                        }
                        result = workers[1].getResult();
                        break;
                    }

                    if(results[0] == 0){
                        threads[1].interrupt();
                        while(threads[2].isAlive()){
                            myQueue.take();
                        }
                        result = workers[2].getResult();
                        break;
                    }

                    if(results[1] >= 0 && results[2] >= 0 && results[1] == results[2]){
                        threads[0].interrupt();
                        result = results[1] == 1;
                        break;
                    }
                counter++;
            }

            return result;
        }

        private boolean solveNOT(CircuitNode[] args)
                throws InterruptedException {
            return !recursive(args[0]);
        }

        private boolean solveOR(CircuitNode[] args)
                throws InterruptedException {
            boolean result = false;

            prepareThreads(args);

            int counter = 0;
            while (counter < args.length) {
                int sonResult = myQueue.take();
                if(sonResult == 1) {
                    result = true;
                    break; // the result is already known
                }
                counter++;
            }

            if (result)
                interruptThreads();

            return result;

        }

        private boolean solveAND(CircuitNode[] args)
                throws InterruptedException {
            boolean result = true;

            prepareThreads(args);

            int counter = 0;
            while (counter < args.length) {
                int sonResult = myQueue.take();
                if(sonResult == 0) {
                    result = false;
                    break; // the result is already known
                }
                counter++;
            }

            if (!result)
                interruptThreads();

            return result;
        }

        private boolean solveGT(CircuitNode[] args, int threshold)
                throws InterruptedException {
            boolean result = false;
            int counterTrue = 0;

            prepareThreads(args);

            int counter = 0;
            while (counter < args.length) {
                int sonResult = myQueue.take();
                if(sonResult == 1) {
                    counterTrue++;
                    if (counterTrue > threshold) {
                        result = true;
                        break; // the result is already known
                    }
                }else{
                    if(args.length - (counter + 1) <= threshold - counterTrue){
                        break; // the result is already known
                    }
                }
                counter++;
            }

            if(counter < args.length)
                interruptThreads();

            return result;
        }

        private boolean solveLT(CircuitNode[] args, int threshold)
                throws InterruptedException {

            if(threshold == 0)
                return false;

            boolean result = true;

            prepareThreads(args);

            int counter = 0;
            int counterTrue = 0;
            while (counter < args.length) {
                int sonResult = myQueue.take();
                if(sonResult == 1) {
                    counterTrue++;
                    if(counterTrue >= threshold) {
                        result = false;
                        break; // the result is already known
                    }
                }else{
                    if((counter + 1) - counterTrue > args.length - threshold){
                        break; // the result is already known
                    }
                }
                counter++;
            }

            if(counter < args.length)
                interruptThreads();

            return result;
        }
    }

    public ParallelCircuitSolver (){
        threads = new HashMap<>(); // contains threads (sons)
    }


    @Override
    public CircuitValue solve(Circuit c) {

        if(!acceptComputations)
            return new BrokenCircuitValue();

        ParallelCircuitValue circuitValue = new ParallelCircuitValue();

        // the result will be pushed to ParallelCircuitValue
        Worker worker = new Worker(c.getRoot(), circuitValue.getQueue());
        Thread thread = new Thread(worker);

        threads.put(circuitValue, thread);

        thread.start();

        return circuitValue;
    }

    @Override
    public void stop() {
        acceptComputations = false;
        for(Thread thread : threads.values()){
            thread.interrupt();
        }
        try{
            for(Thread thread : threads.values()){
                thread.join();
            }
        } catch (InterruptedException e) {
        }
    }
}
