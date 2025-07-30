#!/bin/bash

# Java CBLite Application Startup Script
# This script builds and runs only the Java CBLite application using Docker Compose

set -e

echo "☕ Java CBLite Application Startup"
echo "========================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
DATA_DIR="data"
SERVICE_NAME="java-cpg-app"
EDGE_SERVICE_NAME="couchbase-edge-server"
COMPOSE_FILE="docker-compose.yml"
EDGE_SERVER_HOST="${EDGE_SERVER_HOST:-couchbase-edge-server}"
EDGE_SERVER_PORT="${EDGE_SERVER_PORT:-59840}"
EDGE_SERVER_DATABASE="${EDGE_SERVER_DATABASE:-sampledb}"

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
    
    if [ ! -f "pom.xml" ]; then
        print_error "pom.xml not found. Make sure you're in the project root directory."
        exit 1
    fi
    
    if [ ! -d "src" ]; then
        print_error "src directory not found. Make sure you're in the project root directory."
        exit 1
    fi
    
    if [ ! -f "Dockerfile.java" ]; then
        print_error "Dockerfile.java not found. Make sure you're in the project root directory."
        exit 1
    fi
    
    print_status "Prerequisites check completed"
}

# Create data directories
setup_directories() {
    print_info "Setting up data directories..."
    
    mkdir -p ${DATA_DIR}/cblite
    
    # Set proper permissions
    chmod 755 ${DATA_DIR}
    chmod 755 ${DATA_DIR}/cblite
    
    print_status "Data directories created"
}

# Check if Edge Server is available
check_edge_server() {
    local check_external=${1:-false}
    
    if [ "$check_external" = true ]; then
        print_info "Checking for Edge Server on localhost:${EDGE_SERVER_PORT}..."
        if curl -s http://localhost:${EDGE_SERVER_PORT}/ > /dev/null 2>&1; then
            print_status "Edge Server is available on localhost:${EDGE_SERVER_PORT}"
            return 0
        else
            print_warning "Edge Server not accessible on localhost:${EDGE_SERVER_PORT}"
            return 1
        fi
    else
        print_info "Checking for Edge Server service..."
        if docker compose ps $EDGE_SERVICE_NAME | grep -q "Up\|running"; then
            print_status "Edge Server service is running"
            return 0
        else
            print_warning "Edge Server service is not running"
            return 1
        fi
    fi
}

# Stop existing Java application service
stop_existing() {
    print_info "Stopping existing Java application service..."
    
    docker compose stop $SERVICE_NAME 2>/dev/null || true
    docker compose rm -f $SERVICE_NAME 2>/dev/null || true
    
    print_status "Existing Java application service stopped"
}

# Build Docker image using compose
build_image() {
    print_info "Building Java application with Docker Compose..."
    
    docker compose build $SERVICE_NAME
    
    if [ $? -eq 0 ]; then
        print_status "Docker image built successfully"
    else
        print_error "Failed to build Docker image"
        exit 1
    fi
}

# Start Java application using Docker Compose
start_java_app() {
    local standalone=${1:-false}
    
    print_info "Starting Java CBLite application with Docker Compose..."
    
    print_info "Configuration:"
    echo "  🔄 Write Interval: 30 seconds"
    echo "  ⏱️  Write Timeout: 10 seconds"
    echo "  📊 Sync Timeout: 60 seconds"
    echo "  📦 Max Documents per Batch: 5"
    echo "  🌐 Edge Server: ${EDGE_SERVER_HOST}:${EDGE_SERVER_PORT}"
    echo "  🗃️  Database: ${EDGE_SERVER_DATABASE}"
    echo "  📂 Target Collection: test_collection"
    echo
    
    print_info "Data Location:"
    echo "  📁 CBLite Data: ${DATA_DIR}/cblite/"
    echo "  📄 Database File: ${DATA_DIR}/cblite/sampledb.cblite2/db.sqlite3"
    echo
    
    print_info "Starting Java application (Press Ctrl+C to stop)..."
    
    if [ "$standalone" = true ]; then
        # Override environment variables for standalone mode
        EDGE_SERVER_HOST=host.docker.internal docker compose up $SERVICE_NAME
    else
        docker compose up $SERVICE_NAME
    fi
}

# Local Maven run (alternative to Docker Compose)
run_local_maven() {
    print_info "Running Java application locally with Maven..."
    
    if ! command -v mvn &> /dev/null; then
        print_error "Maven is not installed. Please install Maven first."
        exit 1
    fi
    
    if ! command -v java &> /dev/null; then
        print_error "Java is not installed. Please install Java first."
        exit 1
    fi
    
    # Set environment variables for local run
    export EDGE_SERVER_HOST="localhost"
    export EDGE_SERVER_PORT="${EDGE_SERVER_PORT}"
    export EDGE_SERVER_DATABASE="${EDGE_SERVER_DATABASE}"
    export WRITE_INTERVAL_SECONDS=30
    export WRITE_TIMEOUT_SECONDS=10
    export SYNC_TIMEOUT_SECONDS=60
    export MAX_DOCUMENTS_PER_BATCH=5
    
    print_info "Building application..."
    mvn clean compile
    
    if [ $? -eq 0 ]; then
        print_status "Application built successfully"
    else
        print_error "Failed to build application"
        exit 1
    fi
    
    print_info "Starting CBLite application (Press Ctrl+C to stop)..."
    java -cp target/classes:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout) com.couchbase.example.iot_device.CPGIoTTelemetryGenerator
}

# Main execution
main() {
    local standalone=false
    local local_run=false
    local skip_edge_check=false
    local skip_build=false
    
    # Parse command line arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --standalone)
                standalone=true
                shift
                ;;
            --local)
                local_run=true
                shift
                ;;
            --skip-edge-check)
                skip_edge_check=true
                shift
                ;;
            --skip-build)
                skip_build=true
                shift
                ;;
            *)
                shift
                ;;
        esac
    done
    
    check_prerequisites
    setup_directories
    
    # Check Edge Server if not skipping
    if [ "$skip_edge_check" != true ]; then
        if [ "$standalone" = true ] || [ "$local_run" = true ]; then
            if ! check_edge_server true; then
                print_warning "Edge Server not accessible. Application may not work properly."
                echo
                read -p "Continue anyway? (y/N) " -n 1 -r
                echo
                if [[ ! $REPLY =~ ^[Yy]$ ]]; then
                    print_info "Please start the Edge Server first with: ./run-edge-server.sh"
                    exit 1
                fi
            fi
        else
            if ! check_edge_server false; then
                print_warning "Edge Server service not running. Application may not work properly."
                echo
                read -p "Continue anyway? (y/N) " -n 1 -r
                echo
                if [[ ! $REPLY =~ ^[Yy]$ ]]; then
                    print_info "Please start the Edge Server first with: ./run-edge-server.sh"
                    exit 1
                fi
            fi
        fi
    fi
    
    if [ "$local_run" = true ]; then
        run_local_maven
    else
        stop_existing
        
        if [ "$skip_build" != true ]; then
            build_image
        fi
        
        start_java_app $standalone
    fi
}

# Handle script interruption
trap 'print_info "Stopping Java application..."; docker compose stop $SERVICE_NAME; exit 0' INT

# Check if running with --help
if [[ "$1" == "--help" || "$1" == "-h" ]]; then
    echo "Java CBLite Application Startup Script (Docker Compose)"
    echo
    echo "Usage: $0 [options]"
    echo
    echo "Options:"
    echo "  -h, --help           Show this help message"
    echo "  --standalone         Run without connecting to Edge Server container"
    echo "  --local              Run locally with Maven (not in Docker)"
    echo "  --skip-edge-check    Skip checking for Edge Server availability"
    echo "  --skip-build         Skip building Docker image"
    echo "  --clean              Clean up data directories before starting"
    echo "  --stop               Stop the Java application"
    echo "  --logs               Show current logs"
    echo
    echo "This script will:"
    echo "  1. Check prerequisites (Docker, Docker Compose, project files)"
    echo "  2. Create data directories"
    echo "  3. Optionally check Edge Server availability"
    echo "  4. Build and start Java application with Docker Compose (foreground)"
    echo
    echo "Running Modes:"
    echo "  Default:     Run with Docker Compose connected to Edge Server service"
    echo "  --standalone Run with Docker Compose but connect to Edge Server on localhost"
    echo "  --local      Run directly with Maven (no Docker)"
    echo
    echo "Configuration:"
    echo "  - Service: ${SERVICE_NAME}"
    echo "  - Compose File: ${COMPOSE_FILE}"
    echo "  - Edge Server: ${EDGE_SERVER_HOST}:${EDGE_SERVER_PORT}"
    echo "  - Database: ${EDGE_SERVER_DATABASE}"
    echo
    echo "The service will run in the foreground. Press Ctrl+C to stop."
    echo
    exit 0
fi

# Handle stop option
if [[ "$1" == "--stop" ]]; then
    print_info "Stopping Java application..."
    docker compose stop $SERVICE_NAME 2>/dev/null || print_warning "Service not running"
    docker compose rm -f $SERVICE_NAME 2>/dev/null || print_warning "Service not found"
    print_status "Java application stopped"
    exit 0
fi

# Handle logs option
if [[ "$1" == "--logs" ]]; then
    print_info "Showing Java application logs..."
    docker compose logs -f $SERVICE_NAME
    exit 0
fi

# Handle clean option
if [[ "$1" == "--clean" ]]; then
    print_info "Cleaning up data directories..."
    docker compose stop $SERVICE_NAME 2>/dev/null || true
    docker compose rm -f $SERVICE_NAME 2>/dev/null || true
    rm -rf ${DATA_DIR}/cblite
    print_status "Data directories cleaned"
fi

# Run main function
main "$@" 