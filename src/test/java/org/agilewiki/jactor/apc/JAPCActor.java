/*
 * Copyright 2011 Bill La Forge
 *
 * This file is part of AgileWiki and is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License (LGPL) as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 * or navigate to the following url http://www.gnu.org/licenses/lgpl-2.1.txt
 *
 * Note however that only Scala, Java and JavaScript files are being covered by LGPL.
 * All other files are covered by the Common Public License (CPL).
 * A copy of this license is also included and can be
 * found as well at http://www.opensource.org/licenses/cpl1.0.txt
 */
package org.agilewiki.jactor.apc;

import org.agilewiki.jactor.Actor;
import org.agilewiki.jactor.ExceptionHandler;
import org.agilewiki.jactor.Mailbox;
import org.agilewiki.jactor.ResponseProcessor;
import org.agilewiki.jactor.bufferedEvents.BufferedEventsDestination;
import org.agilewiki.jactor.bufferedEvents.BufferedEventsQueue;
import org.agilewiki.jactor.concurrent.ThreadManager;

/**
 * An asynchronous implementation of Actor.
 */
abstract public class JAPCActor implements Actor {

    /**
     * The inbox and outbox of the actor.
     */
    private APCMailbox mailbox;

    /**
     * Handles callbacks from the mailbox.
     */
    private RequestProcessor requestProcessor = new RequestProcessor() {
        private ExceptionHandler exceptionHandler;

        public ExceptionHandler getExceptionHandler() {
            return exceptionHandler;
        }

        public void setExceptionHandler(ExceptionHandler exceptionHandler) {
            this.exceptionHandler = exceptionHandler;
        }

        @Override
        public void haveEvents() {
            mailbox.dispatchEvents();
        }

        @Override
        public void processRequest(JARequest request) throws Exception {
            JAPCActor.this.processRequest(request.getUnwrappedRequest(), new ResponseProcessor() {
                @Override
                public void process(Object unwrappedResponse) {
                    mailbox.response(unwrappedResponse);
                }
            });
        }
    };

    /**
     * Serves as the originator of requests sent to other actors.
     */
    private APCRequestSource requestSource = new APCRequestSource() {
        @Override
        public void responseFrom(BufferedEventsQueue<JAMessage> eventQueue, JAResponse japcResponse) {
            eventQueue.send(mailbox, japcResponse);
        }

        @Override
        public void send(BufferedEventsDestination<JAMessage> destination, JARequest japcRequest) {
            mailbox.send(destination, japcRequest);
        }
    };

    /**
     * Create a JAPCActor
     *
     * @param threadManager Provides a thread for processing dispatched events.
     */
    public JAPCActor(ThreadManager threadManager) {
        this(new JAPCMailbox(threadManager));
    }

    /**
     * Create a JAPCActor
     * Use this constructor when providing an implementation of APCMailbox
     * other than JAPCMailbox.
     *
     * @param mailbox The actor's mailbox.
     */
    public JAPCActor(APCMailbox mailbox) {
        this.mailbox = mailbox;
    }

    /**
     * Set the initial capacity for buffered outgoing messages.
     *
     * @param initialBufferCapacity The initial capacity for buffered outgoing messages.
     */
    @Override
    final public void setInitialBufferCapacity(int initialBufferCapacity) {
        mailbox.setInitialBufferCapacity(initialBufferCapacity);
    }

    /**
     * Processes a purely synchronous method.
     * An exception will be thrown if the class of the request is not bound to a SyncMethodBinding.
     *
     * @param apcRequestSource The originator of the request.
     * @param request          The request.
     * @return The response.
     * @throws Exception Any uncaught exceptions raised while processing the request.
     */
    @Override
    public Object acceptCall(APCRequestSource apcRequestSource, Object request) throws Exception {
        throw new UnsupportedOperationException(request.getClass().getName());
    }

    /**
     * Wraps and enqueues an unwrapped request in the requester's inbox.
     *
     * @param requestSource    The originator of the request.
     * @param unwrappedRequest The unwrapped request to be sent.
     * @param rd               The request processor.
     */
    @Override
    final public void acceptRequest(APCRequestSource requestSource,
                                    Object unwrappedRequest,
                                    ResponseProcessor rd) {
        JARequest japcRequest = new JARequest(requestSource, requestProcessor, unwrappedRequest, rd);
        requestSource.send(mailbox, japcRequest);
    }

    /**
     * Send an unwrapped request to another actor.
     *
     * @param actor            The target actor.
     * @param unwrappedRequest The unwrapped request.
     * @param rp               The response processor.
     * @throws Exception Any uncaught exceptions thrown while processing the request.
     */
    final protected void send(final Actor actor,
                              final Object unwrappedRequest,
                              final ResponseProcessor rp) throws Exception {
        actor.acceptRequest(requestSource, unwrappedRequest, rp);
    }

    /**
     * The application method for processing requests sent to the actor.
     *
     * @param request           A request.
     * @param responseProcessor The response processor.
     * @throws Exception Any uncaught exceptions raised while processing the request.
     */
    abstract protected void processRequest(Object request, ResponseProcessor responseProcessor)
            throws Exception;

    @Override
    public Mailbox getMailbox() {
        throw new UnsupportedOperationException();
    }
}
