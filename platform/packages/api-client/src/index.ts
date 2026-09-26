import type { components, operations, paths } from './schema';

export type { components, operations, paths };

export type Schemas = components['schemas'];

/** Modelos de requisição da API, gerados do OpenAPI (springdoc). */
export type User = Schemas['User'];
export type LoginRequest = Schemas['LoginRequest'];
export type SignupRequest = Schemas['SignupRequest'];
export type StatusRequest = Schemas['StatusRequest'];
export type DeviceTokenRequest = Schemas['DeviceTokenRequest'];
export type CheckoutRequest = Schemas['CheckoutRequest'];

export const ORDER_STATUS_ACTIONS = ['accept', 'ready', 'reject', 'assign', 'unassign', 'pickup', 'deliver', 'fail', 'cancel'] as const;
export type OrderAction = (typeof ORDER_STATUS_ACTIONS)[number];

/** Ações que uma cozinha pode executar. */
export const KITCHEN_ACTIONS = ['accept', 'ready', 'reject'] as const;
export type KitchenAction = (typeof KITCHEN_ACTIONS)[number];

export type OrderStatusPath = paths['/orders/{id}/status'];
