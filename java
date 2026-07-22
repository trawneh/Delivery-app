const express = require('express');
const http = require('http');
const { Server } = require('socket.io');

const app = express();
const server = http.createServer(app);
const io = new Server(server, { cors: { origin: "*" } });

app.use(express.json());

// قاعدة بيانات مبسطة في الذاكرة (Memory Storage)
let drivers = {}; // الكباتن
let orders = [];  // الطلبات

// 1. API للمطعم لطلب سيارة جديدة
app.post('/api/orders/create', (req, res) => {
    const { restaurantName, customerAddress, deliveryFee, orderAmount } = req.body;
    
    const newOrder = {
        id: 'ORD-' + Math.floor(1000 + Math.random() * 9000),
        restaurantName,
        customerAddress,
        deliveryFee,
        orderAmount,
        status: 'PENDING', // معلق في انتظار كابتن
        assignedDriver: null,
        createdAt: new Date()
    };

    orders.push(newOrder);

    // إرسال إشعار لحظي لجميع الكباتن المتاحين (Broadcast)
    io.emit('NEW_ORDER_AVAILABLE', newOrder);

    res.json({ success: true, message: 'تم إرسال الطلب للكباتن', order: newOrder });
});

// Socket.io للاتصال اللحظي والخرائط
io.on('connection', (socket) => {
    console.log('مستخدم جديد اتصل:', socket.id);

    // تسجيل الكابتن وتحديث موقعه
    socket.on('UPDATE_DRIVER_LOCATION', (data) => {
        // data = { driverId, name, lat, lng, walletBalance }
        drivers[socket.id] = {
            ...data,
            socketId: socket.id,
            status: data.status || 'AVAILABLE'
        };
        // تحديث الخريطة في لوحة التحكم فورا
        io.emit('ADMIN_UPDATE_DRIVERS', Object.values(drivers));
    });

    // أول كابتن يضغط "قبول الطلب"
    socket.on('ACCEPT_ORDER', ({ orderId, driverId, driverName }) => {
        let order = orders.find(o => o.id === orderId && o.status === 'PENDING');

        if (order) {
            const systemCommission = order.deliveryFee * 0.10; // خصم 10% نسبة التطبيق

            // خصم النسبة من محفظة الكابتن
            if (drivers[socket.id]) {
                drivers[socket.id].walletBalance -= systemCommission;
            }

            order.status = 'ACCEPTED';
            order.assignedDriver = { driverId, driverName };

            // إعلام جميع الكباتن إن الطلب انأخذ خلاص
            io.emit('ORDER_TAKEN', { orderId });
            
            // إعلام المطعم إن الكابتن قبل الطلب مع الوقت التقديري
            io.emit(`ORDER_STATUS_${orderId}`, {
                status: 'ACCEPTED',
                driverName: driverName,
                estimatedArrivalMinutes: 8 // وقت تقديري للوصول
            });

            console.log(`الطلب ${orderId} قبله الكابتن ${driverName}. خصم عمولة: ${systemCommission}`);
        } else {
            socket.emit('ORDER_ACCEPT_FAILED', { message: 'للأسف، كابتن آخر أخذ الطلب قبلك!' });
        }
    });

    socket.on('disconnect', () => {
        delete drivers[socket.id];
        io.emit('ADMIN_UPDATE_DRIVERS', Object.values(drivers));
    });
});

const PORT = 3000;
server.listen(PORT, () => console.log(`السيرفر شغال على المنفذ: http://localhost:${PORT}`));
