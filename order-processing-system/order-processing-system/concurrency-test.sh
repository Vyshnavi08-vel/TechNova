#!/usr/bin/env bash
# Fires 20 concurrent orders for 5 units each of product id 5 (Webcam 1080p,
# seeded with only 5 in stock) to demonstrate the system never oversells.
# Expect: exactly 1 order COMPLETED, the rest FAILED/DEAD_LETTERED with an
# "Insufficient stock" reason, and inventory settling at exactly 0 (never negative).

API=http://localhost:8080/api

echo "Stock before:"
curl -s "$API/inventory" | grep -o '"sku":"SKU-005".*quantityAvailable":[0-9]*' 

for i in $(seq 1 20); do
  curl -s -X POST "$API/orders" \
    -H "Content-Type: application/json" \
    -d '{"customerName":"LoadTest-'"$i"'","items":[{"productId":5,"quantity":5}]}' \
    -o /dev/null &
done
wait

echo "Fired 20 concurrent orders. Waiting 3s for async processing..."
sleep 3

echo "Stock after (should be 0, never negative):"
curl -s "$API/inventory" | grep -o '"sku":"SKU-005".*quantityAvailable":[0-9]*'

echo
echo "Order outcomes:"
curl -s "$API/orders" | python3 -c "
import json, sys
orders = json.load(sys.stdin)
from collections import Counter
c = Counter(o['status'] for o in orders if o['customerName'].startswith('LoadTest'))
print(c)
"
