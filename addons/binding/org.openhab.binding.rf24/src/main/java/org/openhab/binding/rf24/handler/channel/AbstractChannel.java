package org.openhab.binding.rf24.handler.channel;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.eclipse.smarthome.core.thing.ChannelUID;
import org.openhab.binding.rf24.internal.serial.ArduinoSerial;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import pl.grzeslowski.smarthome.common.io.id.CommonId;
import pl.grzeslowski.smarthome.common.io.id.HardwareId;
import pl.grzeslowski.smarthome.common.io.id.IdUtils;
import pl.grzeslowski.smarthome.common.io.id.ReceiverId;
import pl.grzeslowski.smarthome.common.io.id.TransmitterId;
import pl.grzeslowski.smarthome.proto.common.Basic.BasicMessage;
import pl.grzeslowski.smarthome.proto.sensor.Sensor.SensorRequest;
import pl.grzeslowski.smarthome.proto.sensor.Sensor.SensorRequest.Builder;
import pl.grzeslowski.smarthome.proto.sensor.Sensor.SensorResponse;

public abstract class AbstractChannel implements Channel, ArduinoSerial.OnSerialMessageListener {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final Updatable updatable;

    private final ArduinoSerial arduinoSerial;
    private final Supplier<Integer> messageIdSupplier;
    private final HardwareId hardwareId;
    private final Map<Integer, ChannelUID> corelationMap = new HashMap<>();
    private final IdUtils idUtils;

    public AbstractChannel(IdUtils idUtils, ArduinoSerial arduinoSerial, Updatable updatable,
            Supplier<Integer> messageIdSupplier, HardwareId hardwareId) {
        this.idUtils = Preconditions.checkNotNull(idUtils);
        this.arduinoSerial = Preconditions.checkNotNull(arduinoSerial);
        this.updatable = Preconditions.checkNotNull(updatable);
        this.messageIdSupplier = Preconditions.checkNotNull(messageIdSupplier);
        this.hardwareId = Preconditions.checkNotNull(hardwareId);
    }

    protected BasicMessage buildBasicMessage() {
        // @formatter:off
        return BasicMessage
                .newBuilder()
                .setDeviceId((int) arduinoSerial.getTransmitterId().getId())
                .setLinuxTimestamp(new Date().getTime())
                .setMessageId(messageIdSupplier.get())
                .build();
        // @formatter:on
    }

    protected Builder newSensorRequest() {
        return SensorRequest.newBuilder().setBasic(buildBasicMessage());
    }

    protected void write(SensorRequest request, ChannelUID channelUID) {
        boolean success = arduinoSerial.write(request);
        int messageId = request.getBasic().getMessageId();
        if (success) {
            synchronized (corelationMap) {
                corelationMap.put(messageId, channelUID);
            }

            CommonId commonId = hardwareId.toCommonId();
            ReceiverId receiverId = idUtils.toReceiverId(commonId);
            TransmitterId transmitterId = idUtils.findTransmitterId(receiverId);
            HardwareId transmitterHardwareId = HardwareId.fromTransmitterId(idUtils, transmitterId);

            // logger.info("Looking on tID {} hID {}", transmitterId.getId(), transmitterHardwareId.getId());
            // Optional<SensorResponse> read = Optional.empty();
            // long start = new Date().getTime();
            // long delay = TimeUnit.SECONDS.toMillis(1);
            // while (!read.isPresent() && start + delay >= new Date().getTime()) {
            // read = wifiOperator.getWiFi().read(new Pipe(transmitterHardwareId.getId()));
            // }
            //
            // if(read.isPresent()) {
            // logger.info("Got response = {}", read.get());
            // } else {
            // logger.info("didnt get response msg ID = {}", messageId);
            // }
        } else {
            logger.warn("Sending message to {} with ID {} was not succesfull", hardwareId, messageId);
        }
    }

    protected ChannelUID findChannelUID(SensorResponse response) {
        Integer messageId = response.getBasic().getMessageId();
        synchronized (corelationMap) {
            if (corelationMap.containsKey(messageId)) {
                return corelationMap.remove(messageId);
            } else {
                // should not ever happen!
                throw new IllegalStateException(String
                        .format("Correlation map should have inside ID %s! SensorResponse = %s.", messageId, response));
            }
        }
    }

    @Override
    public void onMessage(ArduinoSerial serial, SensorResponse response) {
        ReceiverId deviceId = new ReceiverId(response.getBasic().getDeviceId());
        HardwareId hardwareId = HardwareId.fromReceiverId(idUtils, deviceId);
        if (this.hardwareId.equals(hardwareId) && canHandleResponse(response)) {
            processMessage(response);
        }
    }

    @Override
    public void onMessage(ArduinoSerial serial, SensorRequest request) {
        logger.warn("I do not suppert sensor request! {}", request);
    }

    protected abstract void processMessage(SensorResponse response);

    protected abstract boolean canHandleResponse(SensorResponse response);

    protected TransmitterId getTransmitterId() {
        return arduinoSerial.getTransmitterId();
    }

    @Override
    public void close() {
        // TODO dodac usueanie z powiadomein
        // wifiOperator.removeFromNotify(this);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }
}
