#!/bin/bash

echo "=== Testing Gallery-Style Image Endpoint ==="

# Test the new gallery-style endpoint
echo "Testing /edgeai_image_direct endpoint..."
curl -X POST http://192.168.0.15:12345/edgeai_image_direct \
  -H "Content-Type: application/json" \
  -d @payload.json \
  -v

echo -e "\n=== Test Complete ===" 