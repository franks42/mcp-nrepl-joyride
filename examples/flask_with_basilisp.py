#!/usr/bin/env python3
"""
Example Flask application with embedded Basilisp nREPL server for introspection.

This demonstrates how to embed a Basilisp nREPL server in a Python web application,
enabling live introspection and debugging via our MCP-nREPL bridge.

Usage:
    # Start with nREPL enabled
    ENABLE_NREPL=true python flask_with_basilisp.py
    
    # Connect via MCP client
    python3 ../mcp_nrepl_client.py --eval "(py-health-check)" --quiet
    
    # Or load utilities and explore
    python3 ../mcp_nrepl_client.py --tool nrepl-load-file --args '{"file-path": "python_introspection_utils.clj"}' --quiet
"""

import os
import sys
import logging
from datetime import datetime
from flask import Flask, request, jsonify, render_template_string

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Create Flask app
app = Flask(__name__)
app.config.update({
    'SECRET_KEY': 'dev-secret-key',
    'DEBUG': True,
    'DATABASE_URL': 'sqlite:///example.db',
    'REDIS_URL': 'redis://localhost:6379',
    'FEATURE_FLAGS': {
        'new_ui': True,
        'beta_features': False,
        'advanced_metrics': True
    }
})

# Simulate some application state
app_state = {
    'start_time': datetime.now(),
    'request_count': 0,
    'active_sessions': {},
    'cache_stats': {'hits': 0, 'misses': 0},
    'user_data': [
        {'id': 1, 'name': 'Alice', 'email': 'alice@example.com'},
        {'id': 2, 'name': 'Bob', 'email': 'bob@example.com'},
        {'id': 3, 'name': 'Carol', 'email': 'carol@example.com'}
    ]
}

# =============================================================================
# Application Routes
# =============================================================================

@app.route('/')
def index():
    app_state['request_count'] += 1
    return render_template_string('''
    <h1>Flask + Basilisp Introspection Example</h1>
    <p>Application is running with embedded nREPL server!</p>
    <p>Request count: {{ request_count }}</p>
    <p>Start time: {{ start_time }}</p>
    <p>nREPL enabled: {{ nrepl_enabled }}</p>
    
    <h2>Try these introspection commands:</h2>
    <pre>
# System info
python3 ../mcp_nrepl_client.py --eval "(py-system-info)" --quiet

# App health check  
python3 ../mcp_nrepl_client.py --eval "(py-health-check)" --quiet

# Explore Flask app object
python3 ../mcp_nrepl_client.py --eval "(py-explore-object python/app)" --quiet

# Check app configuration
python3 ../mcp_nrepl_client.py --eval "(py-config-summary)" --quiet
    </pre>
    
    <h2>API Endpoints:</h2>
    <ul>
        <li><a href="/api/users">/api/users</a> - List users</li>
        <li><a href="/api/stats">/api/stats</a> - App statistics</li>
        <li><a href="/api/health">/api/health</a> - Health check</li>
    </ul>
    ''', 
    request_count=app_state['request_count'],
    start_time=app_state['start_time'],
    nrepl_enabled=hasattr(app, '_nrepl_server'))

@app.route('/api/users')
def get_users():
    app_state['request_count'] += 1
    return jsonify(app_state['user_data'])

@app.route('/api/stats')
def get_stats():
    app_state['request_count'] += 1
    uptime = datetime.now() - app_state['start_time']
    return jsonify({
        'uptime_seconds': uptime.total_seconds(),
        'request_count': app_state['request_count'],
        'active_sessions': len(app_state['active_sessions']),
        'cache_stats': app_state['cache_stats'],
        'feature_flags': app.config['FEATURE_FLAGS']
    })

@app.route('/api/health')
def health_check():
    app_state['request_count'] += 1
    return jsonify({
        'status': 'healthy',
        'timestamp': datetime.now().isoformat(),
        'version': '1.0.0',
        'nrepl_enabled': hasattr(app, '_nrepl_server')
    })

@app.route('/api/memory-usage')
def memory_usage():
    """Endpoint that can be introspected via nREPL"""
    try:
        import psutil
        process = psutil.Process()
        memory_info = process.memory_info()
        return jsonify({
            'rss_mb': memory_info.rss / 1024 / 1024,
            'vms_mb': memory_info.vms / 1024 / 1024,
            'percent': process.memory_percent()
        })
    except ImportError:
        return jsonify({'error': 'psutil not available'})

# Simulate some business logic functions
def process_user_data(user_data, validate=True):
    """Example business function that can be introspected"""
    if validate:
        required_fields = ['id', 'name', 'email']
        for field in required_fields:
            if field not in user_data:
                raise ValueError(f"Missing required field: {field}")
    
    # Simulate processing
    processed = user_data.copy()
    processed['processed_at'] = datetime.now().isoformat()
    processed['status'] = 'processed'
    
    return processed

def expensive_computation(n=1000000):
    """Function for performance testing"""
    result = sum(i * i for i in range(n))
    return result

# Make functions available at module level for introspection
sys.modules[__name__].process_user_data = process_user_data
sys.modules[__name__].expensive_computation = expensive_computation

# =============================================================================
# Basilisp nREPL Integration
# =============================================================================

def start_nrepl_server(port=7888):
    """Start embedded Basilisp nREPL server"""
    try:
        import basilisp.main
        
        logger.info(f"🔧 Starting Basilisp nREPL server on port {port}")
        
        # Start the nREPL server
        server = basilisp.main.start_nrepl_server(port=port)
        
        # Store reference in app for introspection
        app._nrepl_server = server
        app._nrepl_port = port
        
        logger.info(f"✅ Basilisp nREPL server started successfully")
        logger.info(f"🔗 Connect via: python3 ../mcp_nrepl_client.py --eval \"(+ 1 2 3)\" --quiet")
        
        return server
        
    except ImportError as e:
        logger.error(f"❌ Basilisp not available: {e}")
        logger.error("💡 Install with: pip install basilisp")
        return None
    except Exception as e:
        logger.error(f"❌ Failed to start nREPL server: {e}")
        return None

def setup_introspection_environment():
    """Set up global environment for easier introspection"""
    
    # Make key objects globally available for introspection
    import sys
    module = sys.modules[__name__]
    
    # Expose Flask app and state
    module.app = app
    module.app_state = app_state
    
    # Expose utility functions  
    module.get_app_stats = lambda: {
        'request_count': app_state['request_count'],
        'uptime': (datetime.now() - app_state['start_time']).total_seconds(),
        'routes': [str(rule) for rule in app.url_map.iter_rules()],
        'config_keys': list(app.config.keys())
    }
    
    module.simulate_load = lambda requests=100: [
        app_state.update({'request_count': app_state['request_count'] + 1})
        for _ in range(requests)
    ]
    
    logger.info("🔧 Introspection environment configured")

# =============================================================================
# Application Startup
# =============================================================================

def main():
    """Main application entry point"""
    
    # Setup introspection environment
    setup_introspection_environment()
    
    # Start nREPL server if enabled
    if os.getenv('ENABLE_NREPL', '').lower() in ['true', '1', 'yes']:
        port = int(os.getenv('NREPL_PORT', '7888'))
        start_nrepl_server(port)
    else:
        logger.info("ℹ️  nREPL server disabled. Set ENABLE_NREPL=true to enable")
    
    # Start Flask development server
    debug_mode = os.getenv('FLASK_DEBUG', '').lower() in ['true', '1', 'yes']
    port = int(os.getenv('FLASK_PORT', '5000'))
    
    logger.info(f"🚀 Starting Flask application on port {port}")
    logger.info(f"🌐 Open http://localhost:{port} to view application")
    
    app.run(
        host='0.0.0.0',
        port=port,
        debug=debug_mode,
        threaded=True
    )

if __name__ == '__main__':
    main()