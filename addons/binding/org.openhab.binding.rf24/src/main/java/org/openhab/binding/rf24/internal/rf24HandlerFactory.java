/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.rf24.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.openhab.binding.rf24.rf24BindingConstants;
import org.openhab.binding.rf24.handler.HardwareIdFactory;
import org.openhab.binding.rf24.handler.rf24BaseHandler;
import org.openhab.binding.rf24.wifi.Rf24;
import org.openhab.binding.rf24.wifi.StubWiFi;
import org.openhab.binding.rf24.wifi.WiFi;
import org.openhab.binding.rf24.wifi.WifiOperator;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import pl.grzeslowski.smarthome.common.io.id.HardwareId;
import pl.grzeslowski.smarthome.common.io.id.IdUtils;
import pl.grzeslowski.smarthome.common.io.id.TransmitterId;
import pl.grzeslowski.smarthome.rf24.Rf24Adapter;
import pl.grzeslowski.smarthome.rf24.helpers.ClockSpeed;
import pl.grzeslowski.smarthome.rf24.helpers.Payload;
import pl.grzeslowski.smarthome.rf24.helpers.Pins;
import pl.grzeslowski.smarthome.rf24.helpers.Retry;

/**
 * The {@link rf24HandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Martin Grzeslowski - Initial contribution
 */
public class rf24HandlerFactory extends BaseThingHandlerFactory {
    private static final boolean RPI = "RPi".equals(System.getenv().get("system"));

    private static final Logger logger = LoggerFactory.getLogger(rf24HandlerFactory.class);
    private static final IdUtils ID_UTILS = new IdUtils(Rf24Adapter.MAX_NUMBER_OF_READING_PIPES);
    private static final HardwareIdFactory HARDWARE_ID_FACTORY = new HardwareIdFactory();
    private static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(1);

    // @formatter:off
    private final static Collection<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Lists.newArrayList(rf24BindingConstants.RF24_RECIVER_THING_TYPE);
    // @formatter:on

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        // @formatter:off
        return SUPPORTED_THING_TYPES_UIDS
                .stream()
                .filter(thingTypeUID::equals)
                .findFirst()
                .isPresent();
        // @formatter:on
    }

    private final List<WifiOperator> xs;

    public rf24HandlerFactory() {
        final List<WiFi> wifis = new ArrayList<>();
        if (RPI) {
            // @formatter:off
            Rf24 wifi = new Rf24(
                    new Pins((short) 22, (short) 8, ClockSpeed.BCM2835_SPI_SPEED_8MHZ),
                    new Retry((short)15, (short)15),
                    new Payload((short)128));
            // @formatter:on
            wifis.add(wifi);
            logger.info("I'm working on RPi! Wifi: {}.", wifi);
        } else {
            wifis.add(new StubWiFi());
            wifis.add(new StubWiFi());
        }

        final AtomicLong transmitterId = new AtomicLong(1);
        // @formatter:off
        xs = wifis.stream()
            .map(wifi -> new WifiOperator(ID_UTILS, wifi, new TransmitterId(transmitterId.getAndIncrement()), EXECUTOR))
            .collect(Collectors.toList());
        // @formatter:on
    }

    @Override
    protected void activate(ComponentContext componentContext) {
        // @formatter:off
        xs.stream().forEach(WifiOperator::init);
        // @formatter:on
        super.activate(componentContext);
    }

    @Override
    protected void deactivate(ComponentContext componentContext) {
        super.deactivate(componentContext);
        // @formatter:off
        xs.stream().forEach(WifiOperator::close);
        // @formatter:on
    }

    @Override
    protected ThingHandler createHandler(Thing thing) {
        HardwareId hardwareId = HARDWARE_ID_FACTORY.findHardwareId(thing);
        return new rf24BaseHandler(thing, ID_UTILS, findForPipe(hardwareId), hardwareId);
    }

    private WifiOperator findForPipe(HardwareId hardwareId) {
        // @formatter:off
        final TransmitterId transmitterId = Optional.of(hardwareId)
            .map(hId -> hId.toCommonId())
            .map(cId -> ID_UTILS.toReceiverId(cId))
            .map(rId -> ID_UTILS.findTransmitterId(rId))
            .orElseThrow((() ->
                    new IllegalArgumentException(String.format("Could not found transmitterId for pipe %s!", hardwareId.toString()))));

        return xs.stream()
            .filter(x -> x.geTransmitterId().equals(transmitterId))
            .findAny()
            .orElseThrow(() ->
                new IllegalArgumentException(String.format("Could not find wifi for pipe %s!", hardwareId.toString())));
        // @formatter:on
    }
}
