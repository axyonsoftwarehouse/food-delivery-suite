self.addEventListener('push', (event) => {
  let data = {};
  try {
    data = event.data ? event.data.json() : {};
  } catch (error) {
    data = { title: 'Foodie', body: event.data ? event.data.text() : '' };
  }
  const title = data.title || 'Foodie';
  event.waitUntil(self.registration.showNotification(title, {
    body: data.body || '',
    data: { orderId: data.orderId ?? null },
    tag: data.orderId ? `order-${data.orderId}` : undefined,
  }));
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const orderId = event.notification.data && event.notification.data.orderId;
  const url = orderId ? `/?order=${orderId}` : '/';
  event.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((list) => {
    for (const client of list) {
      if ('focus' in client) { client.focus(); if (url !== '/') client.navigate(url); return; }
    }
    return self.clients.openWindow(url);
  }));
});
