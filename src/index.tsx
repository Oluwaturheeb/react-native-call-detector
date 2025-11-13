import { NativeModules, NativeEventEmitter } from 'react-native';

const { CallDetector } = NativeModules;

// Event emitter for native updates
const callDetectorEmitter = new NativeEventEmitter(CallDetector);

export type CallState = 'Incoming' | 'Offhook' | 'Idle' | 'Unknown';

export interface CallData {
  number: string;
  name?: string;
  type?: 'INCOMING' | 'OUTGOING' | 'MISSED' | 'REJECTED' | 'UNKNOWN';
  date?: string;
  duration?: string;
}

export interface CallEvent {
  state: CallState;
  call: CallData;
}

// Store active subscriptions
const subscriptions: Array<{ remove: () => void }> = [];

/**
 * Subscribe to call state updates
 * Returns an unsubscribe function
 */
export function onCallStateChange(listener: (event: CallEvent) => void) {
  const subscription = callDetectorEmitter.addListener(
    'CallStateUpdate',
    listener
  );
  subscriptions.push(subscription);

  return () => {
    subscription.remove();
    const index = subscriptions.indexOf(subscription);
    if (index > -1) subscriptions.splice(index, 1);
  };
}

/**
 * Start the native call detector
 * No parameters needed; uses app name and icon internally
 */
export function start() {
  CallDetector.startListener();
}

/**
 * Stop the native call detector and remove all listeners
 */
export function stop() {
  CallDetector.stopListener();
  subscriptions.forEach((sub) => sub.remove());
  subscriptions.length = 0;
}

// transaction spool by request id
