import { setupWorker } from 'msw/browser';
import { authHandlers } from './handlers/auth';
import { cardholderHandlers } from './handlers/cardholder';
import { analystHandlers } from './handlers/analyst';
import { managerHandlers } from './handlers/manager';

export const worker = setupWorker(
  ...authHandlers,
  ...cardholderHandlers,
  ...analystHandlers,
  ...managerHandlers
);

export async function startWorker() {
  if (typeof window === 'undefined') return;
  await worker.start({
    onUnhandledRequest: 'bypass',
  });
}
