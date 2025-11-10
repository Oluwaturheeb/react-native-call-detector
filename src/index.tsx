import CallDetector from './NativeCallDetector';

export function multiply(a: number, b: number): number {
  return CallDetector.multiply(a, b);
}
