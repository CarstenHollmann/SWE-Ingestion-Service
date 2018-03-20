/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.n52.stream.seadatacloud.restcontroller.controller;

import org.n52.stream.seadatacloud.restcontroller.service.CloudService;

import java.util.Map;
import org.n52.stream.seadatacloud.restcontroller.model.Stream;
import org.n52.stream.seadatacloud.restcontroller.model.Streams;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author Maurin Radtke <m.radtke@52north.org>
 */
@RestController
@Component
@RequestMapping("/api/streams")
public class StreamController {

    public final String APPLICATION_JSON = "application/json";
    public final String APPLICATION_XML = "application/xml";

    @Autowired
    CloudService service;

    @RequestMapping(value = "", method = RequestMethod.POST, consumes = APPLICATION_JSON)
    public ResponseEntity<String> createJsonStream(
            @RequestBody Map<String, Object> payload) {

        try {
            String streamDefinition = "";
            String streamName = (String) payload.get("name");
            Stream stream = service.getStream(streamName);
            if (stream != null){
                return new ResponseEntity("{Error: Stream " + streamName + " already exists.}", HttpStatus.CONFLICT);
            }
            Map<String, Object> source = (Map<String, Object>) payload.get("source");
            String sourceName = (String) source.get("name");
            Map<String, Object> processor = (Map<String, Object>) payload.get("processor");
            String processorName = (String) processor.get("name");
            Map<String, Object> sink = (Map<String, Object>) payload.get("sink");
            String sinkName = (String) sink.get("name");
            if (sourceName.equals("mqttrabbitsource")) {
                streamDefinition = sourceName;
                String mqttUrl = (String) source.get("url");
                if (mqttUrl != null) {
                    streamDefinition += " --url=" + mqttUrl;
                    String mqttPort = (String) source.get("port");
                    if (mqttPort != null) {
                        streamDefinition += ":" + mqttPort;
                    }
                }
                String mqttTopic = (String) source.get("topic");
                if (mqttTopic != null) {
                    streamDefinition += " --topics=" + mqttTopic;
                }
                String mqttUsername = (String) source.get("username");
                if (mqttUsername != null) {
                    streamDefinition += " --username=" + mqttUsername;
                }
                String mqttPassword = (String) source.get("password");
                if (mqttPassword != null) {
                    streamDefinition += " --password=" + mqttPassword;
                }
            } else if (streamName.equals("some other source")) {

            } else {

            }

            String sinkLabel = (String) sink.get("label");
            streamDefinition += " | " + processorName + " | " + sinkName;

            String result = streamName + " created.";
            this.createStream(streamName, streamDefinition, false);
            return new ResponseEntity(result, HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @RequestMapping(value = "", method = RequestMethod.POST, consumes = APPLICATION_XML)
    public ResponseEntity<String> createJXmlStream(
            @RequestBody String xml) {
        String result = xml;
        return new ResponseEntity(result, HttpStatus.CREATED);
    }

    @RequestMapping(value = "", method = RequestMethod.GET, produces = APPLICATION_JSON)
    public ResponseEntity<Streams> getStreams() {
        Streams result = service.getStreams();
        return new ResponseEntity(result, HttpStatus.OK);
    }

    @RequestMapping(value = "/{streamId}", method = RequestMethod.GET, produces = APPLICATION_JSON)
    public ResponseEntity<Stream> getStream(
            @PathVariable String streamId) {
        Stream result = service.getStream(streamId);
        return new ResponseEntity(result, HttpStatus.OK);
    }

    @RequestMapping(value = "/{streamId}", method = RequestMethod.PUT)
    public ResponseEntity<Stream> putStream(
            @PathVariable String streamId,
            @RequestBody Map<String, Object> payload) {
        Stream stream = service.getStream(streamId);
        if (stream == null) {
            return new ResponseEntity(null, HttpStatus.EXPECTATION_FAILED);
        } else {
            String status = stream.getStatus();
            if (status.equals("deploying")) {
                // what to do when it's currently deploying?
                return new ResponseEntity(status, HttpStatus.CONFLICT);
            } else if (status.equals("undeployed")) {
                // deploy Stream
                service.deployStream(streamId);
                stream.setStatus("deploying");
                return new ResponseEntity(stream, HttpStatus.OK);
            } else if (status.equals("deployed")) {
                // undeploy Stream
                service.undeployStream(streamId);
                stream.setStatus("undeployed");
                return new ResponseEntity(stream, HttpStatus.OK);
            } else {
                // NoSuchStreamDefinitionException ==> Error
                return new ResponseEntity(stream, HttpStatus.NOT_FOUND);
            }
        }
    }

    /**
     * DELETE deletes a stream.
     *
     * @param streamId - name of the stream
     * @return succes or error message
     */
    @RequestMapping(value = "/{streamId}", method = RequestMethod.DELETE)
    public ResponseEntity<String> deleteStream(
            @PathVariable String streamId) {
        String result = service.deleteStream(streamId);
        return new ResponseEntity(result, HttpStatus.OK);
    }

    @RequestMapping(value = "/createStream", method = RequestMethod.GET)
    public ResponseEntity<String> createStream(
            @RequestParam("streamName") String streamName,
            @RequestParam("streamDefinition") String streamDefinition,
            @RequestParam("deploy") boolean deploy
    ) {
        String result = "";
        result = service.createStream(streamName, streamDefinition, deploy);
        return new ResponseEntity(result, HttpStatus.OK);
    }
}
