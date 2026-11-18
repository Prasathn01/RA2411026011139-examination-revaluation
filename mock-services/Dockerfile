# Mock external systems (results system, Razorpay-shaped payment API, document store).
# Plain Node.js, no npm packages needed.
FROM node:24-alpine
WORKDIR /app
COPY mock-services.js .
EXPOSE 3000
CMD ["node", "mock-services.js"]
