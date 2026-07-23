const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');

const app = express();
app.use(cors());
app.use(express.json());

const server = http.createServer(app);
const io = new Server(server, {
  cors: { origin: "*", methods: ["GET", "POST"] }
});

const activeDrivers = new Map();
const activeOrders = new Map();
let restaurants = [];
let registeredDrivers = [];

// إعدادات النظام الافتراضية
let systemSettings = {
  adminPassword: "123", // كلمة مرور الإدارة الافتراضية لحماية عمليات الحذف
  commissionRate: 15,
  baseFare: 1.00,
  autoAccept: true
};

io.on('connection', (socket) => {
  console.log(`> اتصال جديد: ${socket.id}`);

  broadcastData();

  socket.on('UPDATE_LOCATION', (data) => {
    activeDrivers.set(socket.id, {
      socketId: socket.id,
      name: data.name || 'كابتن',
      lat: data.lat,
      lng: data.lng,
      status: data.status || 'متصل',
      earnings: data.earnings || 0,
      completedTrips: data.completedTrips || 0
    });
    broadcastData();
  });

  socket.on('CREATE_ORDER', (orderData) => {
    const orderId = 'ORD-' + Math.floor(1000 + Math.random() * 9000);
    const newOrder = {
      id: orderId,
      clientName: orderData.clientName,
      pickup: orderData.pickup,
      dropoff: orderData.dropoff,
      fare: orderData.fare,
      status: 'قيد الانتظار'
    };
    activeOrders.set(orderId, newOrder);
    broadcastData();
  });

  socket.on('ADD_RESTAURANT', (restData) => {
    restaurants.push({
      id: 'REST-' + Date.now(),
      name: restData.name,
      location: restData.location,
      category: restData.category || 'عام'
    });
    broadcastData();
  });

  // حذف مطعم مع التحقق من كلمة المرور
  socket.on('DELETE_RESTAURANT', ({ id, password }) => {
    if (password === systemSettings.adminPassword) {
      restaurants = restaurants.filter(r => r.id !== id);
      broadcastData();
    } else {
      socket.emit('ACTION_ERROR', 'كلمة مرور الإدارة غير صحيحة!');
    }
  });

  socket.on('ADD_DRIVER_ACCOUNT', (driverData) => {
    registeredDrivers.push({
      id: 'DRV-' + Date.now(),
      name: driverData.name,
      username: driverData.username,
      password: driverData.password,
      role: driverData.role || 'user' // تحديد الصلاحية (Admin أو User عادي)
    });
    broadcastData();
  });

  // حذف كابتن مع التحقق من كلمة المرور
  socket.on('DELETE_DRIVER_ACCOUNT', ({ id, password }) => {
    if (password === systemSettings.adminPassword) {
      registeredDrivers = registeredDrivers.filter(d => d.id !== id);
      broadcastData();
    } else {
      socket.emit('ACTION_ERROR', 'كلمة مرور الإدارة غير صحيحة!');
    }
  });

  // تحديث إعدادات التطبيق
  socket.on('UPDATE_SETTINGS', (newSettings) => {
    systemSettings = { ...systemSettings, ...newSettings };
    broadcastData();
  });

  socket.on('disconnect', () => {
    activeDrivers.delete(socket.id);
    broadcastData();
    console.log(`> انقطع الاتصال: ${socket.id}`);
  });
});

function broadcastData() {
  io.emit('ADMIN_UPDATE_DATA', {
    drivers: Array.from(activeDrivers.values()),
    orders: Array.from(activeOrders.values()),
    restaurants: restaurants,
    registeredDrivers: registeredDrivers,
    settings: systemSettings
  });
}

server.listen(3000, () => {
  console.log('🚀 سيرفر رحلات+ شغال ومستقر على المنفذ 3000');
});
