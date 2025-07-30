#!/bin/bash

# Couchbase Edge Server Startup Script
# This script sets up and starts only the Couchbase Edge Server using Docker Compose

set -e

echo "🏗️  Couchbase Edge Server Startup"
echo "========================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
EDGE_SERVER_CONFIG="edge-server-config.json"
USERS_CONFIG="users.json"
DATA_DIR="data"
EDGE_SERVER_PORT=59840
SERVICE_NAME="couchbase-edge-server"
COMPOSE_FILE="docker-compose.yml"

# Function to print colored output
print_status() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_info() {
    echo -e "${BLUE}📊 $1${NC}"
}

# Check prerequisites
check_prerequisites() {
    print_info "Checking prerequisites..."
    
    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed. Please install Docker first."
        exit 1
    fi
    
    if ! command -v docker compose &> /dev/null; then
        print_error "Docker Compose is not installed. Please install Docker Compose first."
        exit 1
    fi
    
    if [ ! -f "$COMPOSE_FILE" ]; then
        print_error "Docker Compose file not found: $COMPOSE_FILE"
        exit 1
    fi
    
    print_status "Prerequisites check completed"
}

# Create data directories
setup_directories() {
    print_info "Setting up data directories..."
    
    mkdir -p ${DATA_DIR}/edge-server
    mkdir -p ${DATA_DIR}/edge-server/logs
    
    # Set proper permissions
    chmod 755 ${DATA_DIR}
    chmod 755 ${DATA_DIR}/edge-server
    chmod 755 ${DATA_DIR}/edge-server/logs
    
    print_status "Data directories created"
}

# Validate configuration
validate_config() {
    print_info "Validating configuration..."
    
    if [ ! -f "$EDGE_SERVER_CONFIG" ]; then
        print_error "Edge Server configuration file not found: $EDGE_SERVER_CONFIG"
        exit 1
    fi
    
    if [ ! -f "$USERS_CONFIG" ]; then
        print_error "Users configuration file not found: $USERS_CONFIG"
        exit 1
    fi
    
    # Check if placeholders are still present
    if grep -q "your-cluster.apps.cloud.couchbase.com" "$EDGE_SERVER_CONFIG"; then
        print_warning "Please update the Edge Server configuration with your actual Capella cluster details"
        print_info "Edit $EDGE_SERVER_CONFIG and replace:"
        print_info "  - wss://your-cluster.apps.cloud.couchbase.com:4984/sampledb"
        print_info "  - your-username"
        print_info "  - your-password"
        echo
        read -p "Continue anyway? (y/N) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
    
    print_status "Configuration validation completed"
}

# Stop existing Edge Server service
stop_existing() {
    print_info "Stopping existing Edge Server service..."
    
    docker compose stop $SERVICE_NAME 2>/dev/null || true
    docker compose rm -f $SERVICE_NAME 2>/dev/null || true
    
    print_status "Existing Edge Server service stopped"
}

# Start Edge Server using Docker Compose
start_edge_server() {
    print_info "Starting Couchbase Edge Server with Docker Compose..."
    
    print_info "Available Endpoints (once started):"
    echo "  📡 Edge Server REST API: http://localhost:${EDGE_SERVER_PORT}/"
    echo "  📊 Database Info: http://localhost:${EDGE_SERVER_PORT}/sampledb"
    echo "  🔍 Database Queries: http://localhost:${EDGE_SERVER_PORT}/sampledb/_query"
    echo "  📋 All Documents: http://localhost:${EDGE_SERVER_PORT}/sampledb/_all_docs"
    echo
    
    print_info "Starting Edge Server (Press Ctrl+C to stop)..."
    docker compose up $SERVICE_NAME
}

# Main execution
main() {
    check_prerequisites
    setup_directories
    validate_config
    stop_existing
    start_edge_server
}

# Handle script interruption
trap 'print_info "Stopping Edge Server..."; docker compose stop $SERVICE_NAME; exit 0' INT

# Check if running with --help
if [[ "$1" == "--help" || "$1" == "-h" ]]; then
    echo "Couchbase Edge Server Startup Script (Docker Compose)"
    echo
    echo "Usage: $0 [options]"
    echo
    echo "Options:"
    echo "  -h, --help     Show this help message"
    echo "  --build        Build Edge Server image and run service"
    echo "  --clean        Clean up data directories before starting"
    echo "  --stop         Stop the Edge Server"
    echo "  --logs         Show current logs"
    echo
    echo "This script will:"
    echo "  1. Check prerequisites (Docker, Docker Compose)"
    echo "  2. Create data directories"
    echo "  3. Validate configuration files"
    echo "  4. Start Edge Server service with Docker Compose (foreground)"
    echo
    echo "With --build option:"
    echo "  - Builds the Couchbase Edge Server Docker image"
    echo "  - Starts the Edge Server service"
    echo
    echo "Configuration:"
    echo "  - Port: ${EDGE_SERVER_PORT}"
    echo "  - Service: ${SERVICE_NAME}"
    echo "  - Compose File: ${COMPOSE_FILE}"
    echo
    echo "The service will run in the foreground. Press Ctrl+C to stop."
    echo
    exit 0
fi

# Handle stop option
if [[ "$1" == "--stop" ]]; then
    print_info "Stopping Edge Server..."
    docker compose stop $SERVICE_NAME 2>/dev/null || print_warning "Service not running"
    docker compose rm -f $SERVICE_NAME 2>/dev/null || print_warning "Service not found"
    print_status "Edge Server stopped"
    exit 0
fi

# Handle logs option
if [[ "$1" == "--logs" ]]; then
    print_info "Showing Edge Server logs..."
    docker compose logs -f $SERVICE_NAME
    exit 0
fi

# Handle build option
if [[ "$1" == "--build" ]]; then
    print_info "Building Edge Server image and starting service..."
    check_prerequisites
    setup_directories
    validate_config
    
    # Stop existing services
    print_info "Stopping existing Edge Server service..."
    docker compose stop $SERVICE_NAME 2>/dev/null || true
    docker compose rm -f $SERVICE_NAME 2>/dev/null || true
    
    # Build the Edge Server
    print_info "Building Couchbase Edge Server..."
    docker compose build $SERVICE_NAME
    
    print_info "Available Endpoints (once started):"
    echo "  📡 Edge Server REST API: http://localhost:${EDGE_SERVER_PORT}/"
    echo "  📊 Database Info: http://localhost:${EDGE_SERVER_PORT}/sampledb"
    echo "  🔍 Database Queries: http://localhost:${EDGE_SERVER_PORT}/sampledb/_query"
    echo "  📋 All Documents: http://localhost:${EDGE_SERVER_PORT}/sampledb/_all_docs"
    echo
    
    # Start Edge Server
    print_info "Starting Edge Server (Press Ctrl+C to stop)..."
    docker compose up $SERVICE_NAME
    exit 0
fi

# Handle clean option
if [[ "$1" == "--clean" ]]; then
    print_info "Cleaning up data directories..."
    docker compose stop $SERVICE_NAME 2>/dev/null || true
    docker compose rm -f $SERVICE_NAME 2>/dev/null || true
    rm -rf ${DATA_DIR}/edge-server
    print_status "Data directories cleaned"
fi

# Run main function
main 