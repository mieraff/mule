/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSource, Inc.  All rights reserved.  http://www.mulesource.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.example.geomail.components;

import org.mule.DefaultMuleMessage;
import org.mule.api.MuleMessage;
import org.mule.api.MuleException;
import org.mule.api.DefaultMuleException;
import org.mule.api.MuleEventContext;
import org.mule.api.lifecycle.Callable;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import javax.mail.Message;

import org.apache.log4j.Logger;

/**
 * TODO
 */
public class Mail implements Callable
{

    private static final Logger log = Logger.getLogger(Mail.class.getName());

    /**
     * We imple
     *
     * @param eventContext
     * @return
     * @throws Exception
     */
    public Object onCall(MuleEventContext eventContext) throws Exception {

        MuleMessage message = eventContext.getMessage();

        Message mail = (Message) message.getPayload();

        String from = mail.getFrom()[0].toString();
        String[] received = mail.getHeader("Received");

        List list = new ArrayList();

        for (int i = received.length - 1; i >= 0; i--) {

            ReceivedHeader receivedHeader = ReceivedHeader.getInstance(received[i]);
            if (receivedHeader != null && receivedHeader.getFrom() != null) {
                if (!receivedHeader.getFrom().startsWith("localhost") && !receivedHeader.getFrom().startsWith("127.0.0.1")) { // Test
                    String ip = getFromIP(receivedHeader);

                    if (ip != null) {
                        list.add(ip);
                    }
                }
            }

        }

        if (list.isEmpty()) {
            throw new DefaultMuleException("Received e-mail does not provide sender IP information.");
        }

        Map properties = new HashMap();
        properties.put("from.email.address", from);

        MuleMessage result = new DefaultMuleMessage(list, properties);

        return result;
    }

    private String getFromIP(ReceivedHeader receivedHeader) {

        String result = null;

        Matcher matcher = Pattern.compile(".*\\(.*\\[(.*?)\\]\\)", Pattern.DOTALL).matcher(receivedHeader.getFrom());
        if (matcher.matches()) {
            result = matcher.group(1);
        }

        return result;
    }


}
