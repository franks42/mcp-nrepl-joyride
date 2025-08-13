#!/bin/bash
# Master Test Suite - Comprehensive Testing Across All Phases
# Orchestrates phase-specific test suites and provides overall project health check

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
RUN_PHASE1=true
RUN_PHASE2=true
RUN_PHASE3=true
STOP_ON_FAILURE=false
VERBOSE=false

# Parse command line options
while [[ $# -gt 0 ]]; do
    case $1 in
        --phase1-only)
            RUN_PHASE2=false
            RUN_PHASE3=false
            shift
            ;;
        --phase2-only)
            RUN_PHASE1=false
            RUN_PHASE3=false
            shift
            ;;
        --phase3-only)
            RUN_PHASE1=false
            RUN_PHASE2=false
            shift
            ;;
        --skip-phase1)
            RUN_PHASE1=false
            shift
            ;;
        --skip-phase2)
            RUN_PHASE2=false
            shift
            ;;
        --skip-phase3)
            RUN_PHASE3=false
            shift
            ;;
        --stop-on-failure)
            STOP_ON_FAILURE=true
            shift
            ;;
        --verbose)
            VERBOSE=true
            shift
            ;;
        --help)
            echo "Master Test Suite - Comprehensive MCP-nREPL Testing"
            echo ""
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --phase1-only      Run only Phase 1 tests"
            echo "  --phase2-only      Run only Phase 2 tests"
            echo "  --phase3-only      Run only Phase 3 tests"
            echo "  --skip-phase1      Skip Phase 1 tests"
            echo "  --skip-phase2      Skip Phase 2 tests"
            echo "  --skip-phase3      Skip Phase 3 tests"
            echo "  --stop-on-failure  Stop on first test failure"
            echo "  --verbose          Show detailed output"
            echo "  --help             Show this help message"
            echo ""
            echo "Examples:"
            echo "  $0                 # Run all phases"
            echo "  $0 --phase1-only   # Test only minimal server"
            echo "  $0 --skip-phase3   # Skip nREPL integration tests"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

echo -e "${CYAN}🧪 MCP-nREPL Master Test Suite${NC}"
echo -e "${CYAN}==============================${NC}"
echo "Project root: $PROJECT_ROOT"
echo "Timestamp: $(date)"
echo

cd "$PROJECT_ROOT"

# Test results tracking
TOTAL_PHASES=0
PASSED_PHASES=0
FAILED_PHASES=()

run_phase_test() {
    local phase_name="$1"
    local phase_script="$2"
    local phase_description="$3"
    
    TOTAL_PHASES=$((TOTAL_PHASES + 1))
    
    echo -e "${BLUE}╔══════════════════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║ $phase_name - $phase_description${NC}"
    echo -e "${BLUE}╚══════════════════════════════════════════════════════════════════════════════╝${NC}"
    echo
    
    local start_time=$(date +%s)
    
    if [ "$VERBOSE" = true ]; then
        if "$phase_script"; then
            local result="PASSED"
            PASSED_PHASES=$((PASSED_PHASES + 1))
        else
            local result="FAILED"
            FAILED_PHASES+=("$phase_name")
        fi
    else
        if output=$("$phase_script" 2>&1); then
            local result="PASSED"
            PASSED_PHASES=$((PASSED_PHASES + 1))
            echo -e "${GREEN}✅ $phase_name completed successfully${NC}"
        else
            local result="FAILED"
            FAILED_PHASES+=("$phase_name")
            echo -e "${RED}❌ $phase_name failed${NC}"
            if [ "$VERBOSE" = true ]; then
                echo "Error output:"
                echo "$output"
            fi
        fi
    fi
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    
    echo -e "${CYAN}Phase $phase_name: $result (${duration}s)${NC}"
    echo
    
    if [ "$result" = "FAILED" ] && [ "$STOP_ON_FAILURE" = true ]; then
        echo -e "${RED}❌ Stopping on failure as requested${NC}"
        exit 1
    fi
}

# Environment check
echo -e "${YELLOW}=== Environment Check ===${NC}"
echo "Checking required tools and dependencies..."

# Check Babashka
if command -v bb &> /dev/null; then
    echo -e "${GREEN}✅ Babashka available: $(bb --version)${NC}"
else
    echo -e "${RED}❌ Babashka not found${NC}"
    exit 1
fi

# Check UV
if command -v uv &> /dev/null; then
    echo -e "${GREEN}✅ UV available: $(uv --version)${NC}"
else
    echo -e "${RED}❌ UV not found${NC}"
    exit 1
fi

# Check Python
if command -v python3 &> /dev/null; then
    echo -e "${GREEN}✅ Python available: $(python3 --version)${NC}"
else
    echo -e "${RED}❌ Python3 not found${NC}"
    exit 1
fi

echo

# Pre-test cleanup
echo -e "${YELLOW}=== Pre-Test Cleanup ===${NC}"
echo "Cleaning up any existing test processes..."

# Stop any running nREPL servers
if [ -f ".nrepl-test-server.json" ]; then
    echo "Stopping existing test nREPL server..."
    uv run python scripts/nrepl_test_server.py stop > /dev/null 2>&1 || true
fi

# Kill any lingering processes
pkill -f "mcp_server/core.clj" > /dev/null 2>&1 || true
pkill -f "stdio_mcp_client.py" > /dev/null 2>&1 || true

echo -e "${GREEN}✅ Cleanup completed${NC}"
echo

# Run phase tests
echo -e "${YELLOW}=== Phase Testing ===${NC}"

if [ "$RUN_PHASE1" = true ]; then
    run_phase_test "Phase 1" "./scripts/test-phase1.sh" "Minimal MCP Server with Debug Tools"
fi

if [ "$RUN_PHASE2" = true ]; then
    run_phase_test "Phase 2" "./scripts/test-phase2.sh" "Core MCP Functions (nrepl-connect, send-message, get-result)"
fi

if [ "$RUN_PHASE3" = true ]; then
    run_phase_test "Phase 3" "./scripts/test-phase3.sh" "Real nREPL Communication and Integration"
fi

# Final results
echo -e "${CYAN}╔══════════════════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║ MASTER TEST SUITE RESULTS${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════════════════════════════════════════╝${NC}"
echo
echo "Total phases tested: $TOTAL_PHASES"
echo "Phases passed: $PASSED_PHASES"
echo "Phases failed: $((TOTAL_PHASES - PASSED_PHASES))"

if [ ${#FAILED_PHASES[@]} -gt 0 ]; then
    echo
    echo -e "${RED}Failed phases:${NC}"
    for phase in "${FAILED_PHASES[@]}"; do
        echo -e "${RED}  ❌ $phase${NC}"
    done
fi

echo
echo "Test completed: $(date)"

if [ $PASSED_PHASES -eq $TOTAL_PHASES ]; then
    echo -e "${GREEN}🎉 All phases passed! MCP-nREPL bridge is fully functional.${NC}"
    exit 0
else
    echo -e "${RED}❌ Some phases failed. See individual phase results above.${NC}"
    echo -e "${YELLOW}💡 Use individual phase test scripts for detailed debugging.${NC}"
    exit 1
fi