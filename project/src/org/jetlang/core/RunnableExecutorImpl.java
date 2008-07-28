package org.jetlang.core;

import java.util.ArrayList;
import java.util.List;

/// <summary>
/// Default implementation.

/// </summary>
public class RunnableExecutorImpl implements RunnableExecutor {
    private final Object _lock = new Object();
    private boolean _running = true;

    private final List<Runnable> _commands = new ArrayList<Runnable>();
    private final List<Stopable> _onStop = new ArrayList<Stopable>();

    private final RunnableInvoker _commandRunner;

    public RunnableExecutorImpl() {
        _commandRunner = new RunnableInvokerImpl();
    }

    public RunnableExecutorImpl(RunnableInvoker executor) {
        _commandRunner = executor;
    }

    /// <summary>
    /// Queue command.
    /// </summary>
    /// <param name="command"></param>
    public void execute(Runnable command) {
        synchronized (_lock) {
            _commands.add(command);
            _lock.notify();
        }
    }

    /// <summary>
    /// Remove all commands.
    /// </summary>
    /// <returns></returns>
    public Runnable[] DequeueAll() {
        synchronized (_lock) {
            if (ReadyToDequeue()) {
                Runnable[] toReturn = _commands.toArray(new Runnable[_commands.size()]);
                _commands.clear();
                return toReturn;
            } else {
                return null;
            }
        }
    }

    private boolean ReadyToDequeue() {
        while (_commands.size() == 0 && _running) {
            try {
                _lock.wait();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        return _running;
    }

    /// <summary>
    /// Remove all commands and execute.
    /// </summary>
    /// <returns></returns>
    public boolean ExecuteNextBatch() {
        Runnable[] toExecute = DequeueAll();
        if (toExecute == null) {
            return false;
        }
        _commandRunner.executeAll(toExecute);
        return true;
    }

    /// <summary>
    /// Execute commands until stopped.
    /// </summary>
    public void run() {
        while (ExecuteNextBatch()) {
        }
    }

    /// <summary>
    /// Stop consuming events.
    /// </summary>
    public void stop() {
        synchronized (_lock) {
            for (Stopable r : _onStop)
                r.stop();
            _running = false;
            _lock.notify();
        }
    }

    public void addOnStop(Stopable r) {
        synchronized (_lock) {
            _onStop.add(r);
        }
    }

    public boolean removeOnStop(Stopable stopable) {
        synchronized (_lock) {
            return _onStop.remove(stopable);
        }
    }
}
