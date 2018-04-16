/*
 * Copyright (C) 2018-2018 52°North Initiative for Geospatial Open Source
 * Software GmbH
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 as published
 * by the Free Software Foundation.
 *
 * If the program is linked with libraries which are licensed under one of
 * the following licenses, the combination of the program with the linked
 * library is not considered a "derivative work" of the program:
 *
 *     - Apache License, version 2.0
 *     - Apache Software License, version 1.0
 *     - GNU Lesser General Public License, version 3
 *     - Mozilla Public License, versions 1.0, 1.1 and 2.0
 *     - Common Development and Distribution License (CDDL), version 1.0
 *
 * Therefore the distribution of the program linked with libraries licensed
 * under the aforementioned licenses, is permitted by the copyright holders
 * if the distribution is compliant with both the GNU General Public
 * License version 2 and the aforementioned licenses.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 */
package org.n52.stream.seadatacloud.restcontroller.controller;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import org.n52.shetland.ogc.sensorML.AbstractProcess;
import org.n52.shetland.ogc.sensorML.elements.SmlComponent;
import org.n52.shetland.ogc.sensorML.elements.SmlIo;
import org.n52.shetland.ogc.sensorML.v20.AggregateProcess;
import org.n52.shetland.ogc.sensorML.v20.SmlDataInterface;
import org.n52.shetland.ogc.swe.SweDataRecord;
import org.n52.shetland.ogc.swe.SweField;
import org.n52.shetland.ogc.swe.simpleType.SweText;
import org.n52.stream.seadatacloud.restcontroller.model.AppOption;
import org.n52.stream.seadatacloud.restcontroller.model.Source;
import org.n52.stream.seadatacloud.restcontroller.model.Stream;
import org.n52.stream.seadatacloud.restcontroller.model.Streams;
import org.n52.stream.seadatacloud.restcontroller.service.CloudService;
import org.n52.stream.seadatacloud.restcontroller.util.DataRecordDefinitions;
import org.n52.stream.seadatacloud.restcontroller.util.StreamNameURLs;
import org.n52.stream.util.DecoderHelper;
import org.n52.svalbard.decode.DecoderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ImportResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author Maurin Radtke <m.radtke@52north.org>
 */
@RestController
@Component
@ImportResource("classpath*:/svalbard-*.xml")
@RequestMapping("/api/streams")
public class StreamController {

    private static final Logger LOG = LoggerFactory.getLogger(StreamController.class);

    public final String APPLICATION_JSON = "application/json";
    public final String APPLICATION_XML = "application/xml";
    HttpHeaders headers = new HttpHeaders();

    @Autowired
    CloudService service;

    @Autowired
    AppController appController;

    @Autowired
    private DecoderRepository decoderRepository;

    @Autowired
    private DataRecordDefinitions dataRecordDefinitions;

    @Autowired
    private StreamNameURLs streamNameURLs;

    @PostConstruct
    public void init() {
        this.dataRecordDefinitions.add("https://52north.org/swe-ingestion/mqtt/3.1", "mqtt-source-rabbit");
        headers.setContentType(MediaType.APPLICATION_JSON);
    }

    @RequestMapping(value = "", method = RequestMethod.POST, consumes = APPLICATION_XML, produces = APPLICATION_JSON)
    public ResponseEntity<Stream> uploadConfig(
            @RequestBody byte[] requestBody) {
        String streamName = UUID.randomUUID().toString();
        try {
            String streamXML = new String(requestBody);

            DecoderHelper helper = new DecoderHelper();
            decoderRepository.init();
            helper.setDecoderRepository(decoderRepository);

            Object decode = helper.decode(streamXML);

            if (decode instanceof AggregateProcess) {
                ArrayList<SmlComponent> al = (ArrayList<SmlComponent>) ((AggregateProcess) decode).getComponents();
                SmlComponent comp = al.get(0);
                // TODO: instanceof Abfragen:
                AbstractProcess asml = (AbstractProcess) comp.getProcess();
                ArrayList<SmlIo> smlOutputs = (ArrayList<SmlIo>) asml.getOutputs();
                SmlIo smlIo = smlOutputs.get(0);
                SmlDataInterface smlDataInterface = (SmlDataInterface) smlIo.getIoValue();
                SweDataRecord sdr = smlDataInterface.getInterfaceParameters();

                String sdrDefinition = sdr.getDefinition();
                String sourceName = "";
                // get Source:
                Source source;

                if (dataRecordDefinitions.hasDataRecordDefinition(sdrDefinition)) {
                    source = appController.getSourceByName(dataRecordDefinitions.getSourceType(sdrDefinition));
                } else {
                    return new ResponseEntity("{\"error\":\"No supported Source found for DataRecord definition '" + sdrDefinition + "'\"}", HttpStatus.NOT_FOUND);
                }
                if (source == null) {
                    return new ResponseEntity("{ \"error\": \"DataRecord definition '" + sdrDefinition + "' is supposed to be supported by Source '" + sourceName + "', but Source '" + sourceName + "' not found.\"}", HttpStatus.NOT_FOUND);
                }
                sourceName = source.getName();

                LinkedList<SweField> sweFields = (LinkedList<SweField>) sdr.getFields();

                String streamSourceDefinition = "";
                String streamDefinition = "";

                for (SweField current : sweFields) {
                    SweText sweText = (SweText) current.getElement();
                    String optionUrl = sweText.getDefinition();
                    String appOptionName;
                    if (optionUrl.indexOf('#') > -1) {
                        appOptionName = optionUrl.substring(optionUrl.lastIndexOf('#') + 1);
                    } else {
                        return new ResponseEntity("{ \"error\": \"swe:Text definition '" + optionUrl + "' requires a hashtag ( # ) option.\"}", HttpStatus.BAD_REQUEST);
                    }
                    AppOption ao = appController.getSourceOptionByName(source, appOptionName);
                    if (ao == null) {
                        return new ResponseEntity("{ \"error\": \"Option '" + appOptionName + "' is not supported by source '" + sourceName + "'.\"}", HttpStatus.BAD_REQUEST);
                    }
                    streamSourceDefinition += " --" + ao.getName() + "=" + sweText.getValue();
                };
                if (streamSourceDefinition.length() > 0) {
                    streamDefinition = sourceName + streamSourceDefinition + " ";
                } else {
                    streamDefinition = sourceName + " ";
                }

                // TODO: parse processor...
                streamDefinition += "| marine-processor ";
                // TODO: parse sink...
                streamDefinition += "| log-sink";

                Stream createdStream = null;
                streamName = "s" + streamName;
                LOG.info(streamName);
                LOG.info(streamDefinition);
                Future<Stream> futureStream = service.createStream(streamName, streamDefinition, false);

                createdStream = futureStream.get(15, TimeUnit.SECONDS);

                if (createdStream != null) {
                    this.streamNameURLs.add(streamName, streamXML);
                    return new ResponseEntity(createdStream, HttpStatus.CREATED);
                } else {
                    return new ResponseEntity(null, headers, HttpStatus.CONFLICT);
                }
            } else {
                return new ResponseEntity(null, headers, HttpStatus.BAD_REQUEST);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity(e.getMessage(), headers, HttpStatus.BAD_REQUEST);
        }
    }

    @RequestMapping(value = "", method = RequestMethod.GET, produces = APPLICATION_JSON)
    public ResponseEntity<Streams> getStreams() {
        Streams result = service.getStreams();
        return new ResponseEntity(result, headers, HttpStatus.OK);
    }

    @RequestMapping(value = "/{streamId}", method = RequestMethod.GET, produces = APPLICATION_JSON)
    public ResponseEntity<Stream> getStream(
            @PathVariable String streamId) {
        Stream result = service.getStream(streamId);
        if (result == null) {
            return new ResponseEntity("{ \"error\": \"stream with name '" + streamId + "' not found.\"}", headers, HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity(result, headers, HttpStatus.OK);
    }

}
