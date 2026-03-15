from http.server import HTTPServer, BaseHTTPRequestHandler
import json
import os
import time

DATA_FILE = '/opt/location-api/location.json'


class LocationHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path == '/api/location':
            length = int(self.headers.get('Content-Length', 0))
            body = self.rfile.read(length)
            try:
                data = json.loads(body)
                data['received_at'] = int(time.time())
                with open(DATA_FILE, 'w') as f:
                    json.dump(data, f, ensure_ascii=False)
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps({'status': 'ok'}).encode())
                print(f'[{time.strftime("%Y-%m-%d %H:%M:%S")}] Received: lat={data.get("lat")}, lng={data.get("lng")}')
            except Exception as e:
                self.send_response(400)
                self.end_headers()
                self.wfile.write(json.dumps({'error': str(e)}).encode())
        else:
            self.send_response(404)
            self.end_headers()

    def do_GET(self):
        if self.path == '/api/location':
            if os.path.exists(DATA_FILE):
                with open(DATA_FILE, 'r') as f:
                    data = json.load(f)
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps(data, ensure_ascii=False).encode())
            else:
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps({'status': 'no_data'}).encode())
        elif self.path == '/api/ping':
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({'status': 'pong'}).encode())
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        print(f'[{time.strftime("%Y-%m-%d %H:%M:%S")}] {args[0]}')


if __name__ == '__main__':
    port = int(os.environ.get('PORT', 8099))
    server = HTTPServer(('0.0.0.0', port), LocationHandler)
    print(f'Location API running on port {port}')
    server.serve_forever()
