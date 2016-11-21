package org.openhab.binding.rf24.wifi;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import pl.grzeslowski.smarthome.common.io.id.HardwareId;
import pl.grzeslowski.smarthome.common.io.id.IdUtils;
import pl.grzeslowski.smarthome.common.io.id.TransmitterId;
import pl.grzeslowski.smarthome.proto.sensor.Sensor.SensorResponse;
import pl.grzeslowski.smarthome.rf24.helpers.Pipe;

public class Rf24Thread implements AutoCloseable, Runnable {
    private Logger logger = LoggerFactory.getLogger(Rf24Thread.class);

    private final AtomicBoolean exit = new AtomicBoolean(false);
    private final WiFi wifi;
    private final Collection<Pipe> pipes;
    private final Set<OnMessage> notify = new HashSet<>();

    public Rf24Thread(IdUtils idUtils, WiFi wifi, TransmitterId transmitterId) {
        this.wifi = Preconditions.checkNotNull(wifi);
        Preconditions.checkNotNull(transmitterId);
        // @formatter:off
        this.pipes = idUtils.findReceiversForTransmitter(transmitterId)
                .stream()
                .map(idUtils::toCommonId)
                .map(HardwareId::fromCommonId)
                .map(HardwareId::getId)
                .map(id -> new Pipe(id))
                .collect(Collectors.toList());
        // @formatter:on
    }

    @Override
    public void run() {
        // TODO
        // Preconditions.checkArgument(wifi.isInit());
        // or
        // if (!wifi.isInit()) return; // this is not the best cause thread will end
        // or
        // while(!wifi.isInit()) sleep(xxx);

        // if closed do not work anymore
        if (exit.get()) {
            return;
        }

        Optional<SensorResponse> read = wifi.read(pipes);
        if (read.isPresent()) {
            SensorResponse response = read.get();

            synchronized (notify) {
                notify.stream().forEach(message -> message.onMessage(response));
            }
        }
    }

    @Override
    public void close() {
        exit.set(true);
    }

    public void addToNotify(OnMessage message) {
        Preconditions.checkNotNull(message);
        synchronized (notify) {
            notify.add(message);
        }
    }

    public void removeFromNotify(OnMessage message) {
        Preconditions.checkNotNull(message);
        synchronized (notify) {
            notify.remove(Preconditions.checkNotNull(message));
        }
    }

    public static interface OnMessage {
        void onMessage(SensorResponse response);
    }
}