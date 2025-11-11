// CallDetector.ts
import { NativeModules, NativeEventEmitter } from 'react-native';

const { CallDetectorModule } = NativeModules;

export type CallState = 'Incoming' | 'Offhook' | 'Disconnected' | 'Missed';

export interface CallStateEvent {
  state: CallState;
  number: string | null;
}

// Create a typed event emitter
const emitter = new NativeEventEmitter(CallDetectorModule);

// Type-safe subscription object
type Listener = { remove: () => void };
let listener: Listener | null = null;

/**
 * Start listening for call state changes.
 */
export function start() {
  CallDetectorModule.startListener();
}

/**
 * Stop listening for call state changes.
 */
export function stop() {
  CallDetectorModule.stopListener();
  listener?.remove();
  listener = null;
}

/**
 * Subscribe to call state changes.
 * Returns an unsubscribe function.
 */
export function onChange(
  callback: (event: CallStateEvent) => void
): () => void {
  // Wrap the callback to enforce type
  listener = emitter.addListener('CallStateUpdate', (event: any) => {
    callback(event as CallStateEvent);
  });

  return () => listener?.remove();
}

export const CallStates: Record<CallState, CallState> =
  CallDetectorModule.getConstants?.() ?? {
    Incoming: 'Incoming',
    Offhook: 'Offhook',
    Disconnected: 'Disconnected',
    Missed: 'Missed',
  };

export default {
  start,
  stop,
  onChange,
  CallStates,
};
