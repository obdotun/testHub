import { useEffect, useRef, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

export function useWebSocket(topic, onMsg) {
  const clientRef = useRef(null);
  const onMsgRef  = useRef(onMsg);
  onMsgRef.current = onMsg;

  useEffect(() => {
    if (!topic) return;

    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 3000,
      onConnect: () => {
        client.subscribe(topic, (frame) => {
          try {
            onMsgRef.current(JSON.parse(frame.body));
          } catch {
            onMsgRef.current({ text: frame.body, level: 'INFO' });
          }
        });
      },
    });

    client.activate();
    clientRef.current = client;
    return () => client.deactivate();
  }, [topic]);

  const disconnect = useCallback(() => {
    clientRef.current?.deactivate();
  }, []);

  return { disconnect };
}