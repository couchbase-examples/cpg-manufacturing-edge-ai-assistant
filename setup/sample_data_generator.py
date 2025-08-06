import random
import uuid
from datetime import datetime, timedelta
from typing import List, Dict, Any
import logging
import json
from pathlib import Path
from couchbase_client import CouchbaseClient

logger = logging.getLogger(__name__)

class SampleDataGenerator:
    """Generate sample manufacturing data for the demo following WriteDataApp structure."""
    
    # --- Generation Configuration ---
    NUM_PLANTS = 2
    NUM_PRODUCTION_LINES_PER_PLANT = 3
    NUM_MACHINES_PER_LINE = 2
    NUM_ALERTS = 50

    # Plant configurations - aligned with WriteDataApp
    PLANTS = [
        {"plant_id": "CPG_PLANT_001", "name": "CPG Manufacturing Plant - Austin", "location": "Austin, TX"},
        {"plant_id": "CPG_PLANT_002", "name": "CPG Manufacturing Plant - Denver", "location": "Denver, CO"}
    ]

    # Production line types per plant - aligned with WriteDataApp
    PRODUCTION_LINE_TYPES = [
        "BEVERAGE_LINE_A", "BEVERAGE_LINE_B", "WATER_LINE_A", 
        "JUICE_LINE_A", "ENERGY_LINE_A", "PACKAGING_LINE_A"
    ]

    # Machine configurations following WriteDataApp pattern
    MACHINE_TYPES = [
        "beverage_filling_line", "water_filling_line", "juice_filling_line",
        "conveyor_system", "quality_checker", "packaging_unit"
    ]
    
    PRODUCT_TYPES = [
        "carbonated_beverage", "sparkling_water", "fruit_juice", 
        "energy_drink", "juice_beverage", "packaged_product"
    ]

    # Product codes mapping from WriteDataApp
    PRODUCT_CODES = {
        "carbonated_beverage": ["CB_COLA_355", "CB_ORANGE_355", "CB_LEMON_355", "CB_GINGER_355"],
        "sparkling_water": ["SW_PLAIN_330", "SW_LEMON_330", "SW_LIME_330", "SW_BERRY_330"],
        "fruit_juice": ["FJ_APPLE_1L", "FJ_ORANGE_1L", "FJ_GRAPE_1L", "FJ_TROPICAL_1L"],
        "energy_drink": ["ED_BOOST_250", "ED_POWER_250", "ED_ULTRA_250", "ED_SPORT_250"],
        "juice_beverage": ["JB_APPLE_500", "JB_ORANGE_500", "JB_GRAPE_500", "JB_MIXED_500"],
        "packaged_product": ["PP_VARIETY_MIX", "PP_BULK_PACK", "PP_PREMIUM_SET"]
    }

    # Error codes from WriteDataApp DeviceConfiguration
    ERROR_CODES = [
        "E001", "E004", "E005", "E009", "E010", "E012", "E014", "E018", 
        "E019", "E022", "E023", "E024", "E027", "E030", "E034", "E036", 
        "E037", "E043", "E045", "E048", "E050", "E052"
    ]

    # Operators and technicians
    OPERATORS = [
        "John Smith", "Maria Garcia", "David Chen", "Sarah Johnson", "Mike Wilson",
        "Lisa Brown", "Tom Anderson", "Emma Davis", "Carlos Rodriguez", "Anna Kim"
    ]
    
    TECHNICIANS = [
        "Bob Miller", "Alice Cooper", "Frank Thompson", "Diana Prince", "Steve Rogers"
    ]

    # Additional machines for alert generation (WriteDataApp MACHINE_001 generates live alerts)
    ADDITIONAL_MACHINES = [
        {"machine_id": "MACHINE_002", "machine_type": "beverage_filling_line", "production_line": "BEVERAGE_LINE_B", "product_type": "juice_beverage"},
        {"machine_id": "MACHINE_003", "machine_type": "beverage_filling_line", "production_line": "BEVERAGE_LINE_C", "product_type": "energy_drink"},
        {"machine_id": "MACHINE_004", "machine_type": "water_filling_line", "production_line": "WATER_LINE_A", "product_type": "sparkling_water"},
        {"machine_id": "MACHINE_005", "machine_type": "juice_filling_line", "production_line": "JUICE_LINE_A", "product_type": "fruit_juice"},
        {"machine_id": "CONV_001", "machine_type": "conveyor_system", "production_line": "BEVERAGE_LINE_A", "product_type": "carbonated_beverage"},
        {"machine_id": "CONV_002", "machine_type": "conveyor_system", "production_line": "BEVERAGE_LINE_B", "product_type": "juice_beverage"},
        {"machine_id": "QC_001", "machine_type": "quality_checker", "production_line": "BEVERAGE_LINE_A", "product_type": "carbonated_beverage"},
        {"machine_id": "QC_002", "machine_type": "quality_checker", "production_line": "BEVERAGE_LINE_B", "product_type": "juice_beverage"},
    ]

    def __init__(self, couchbase_client: CouchbaseClient):
        self.couchbase_client = couchbase_client
        self.alert_templates = self._load_alert_templates()
        self.alert_counter = 0
        self.generated_machines = []  # Track machines for alert generation
    
    def _load_alert_templates(self) -> List[Dict[str, Any]]:
        """Load alert templates from external JSON file."""
        try:
            template_path = Path(__file__).with_name("alert_templates.json")
            with template_path.open("r", encoding="utf-8") as f:
                templates = json.load(f)
            
            logger.info(f"Loaded {len(templates)} alert templates from {template_path.name}")
            return templates
        except Exception as exc:
            logger.error(f"Failed to load alert templates JSON: {exc}")
            return []

    def _store_document_directly(self, doc_id: str, document: Dict[str, Any], collection_type: str):
        """Store document directly as dict without model conversion."""
        # Remove the entity's own identifier from the body before persisting.
        doc_copy = document.copy()
        id_field_map = {
            "plants": "plant_id",
            "production_lines": "production_line_id",
            "machines": "machine_id",
            "alerts": "alert_id",
        }
        id_field = id_field_map.get(collection_type)
        if id_field:
            doc_copy.pop(id_field, None)
        self.couchbase_client.store_document(
            doc_id, doc_copy, collection_type=collection_type
        )
    
    def generate_plants(self) -> List[Dict[str, Any]]:
        """Generate plant documents with identity only - aligned with WriteDataApp."""
        plants = []
        
        for idx, plant_config in enumerate(self.PLANTS):
            plant = {
                "plant_id": plant_config["plant_id"],
                "name": plant_config["name"],
                "location": plant_config["location"],
                "status": "operational",
                "created_date": datetime.utcnow().isoformat() + "Z"
            }
            
            # Add WriteDataApp specific details for the primary plant
            if idx == 0:  # First plant hosts WriteDataApp machine
                plant.update({
                    "plant_code": "CPG-AUS-001",
                    "facility_type": "Primary Manufacturing",
                    "production_capacity": "High Volume",
                    "quality_certification": ["ISO 9001", "FDA", "HACCP"],
                    "shift_pattern": "24/7 Continuous",
                    "timezone": "America/Chicago",
                    "emergency_contact": "+1-512-555-0100",
                    "plant_manager": "Sarah Johnson",
                    "maintenance_supervisor": "Mike Wilson"
                })
            else:
                plant.update({
                    "plant_code": f"CPG-DEN-00{idx}",
                    "facility_type": "Secondary Manufacturing",
                    "production_capacity": "Medium Volume",
                    "quality_certification": ["ISO 9001", "FDA"],
                    "shift_pattern": "3-Shift",
                    "timezone": "America/Denver"
                })
            
            plants.append(plant)
            
            # Store in Couchbase
            self._store_document_directly(
                plant['plant_id'],
                plant,
                "plants"
            )
        
        logger.info(f"Generated {len(plants)} plants (primary plant hosts WriteDataApp machine)")
        return plants
    
    def generate_production_lines(self, plants: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """Generate production line documents with plant references - aligned with WriteDataApp."""
        production_lines = []
        beverage_line_a_created = False
        
        for plant_idx, plant in enumerate(plants):
            for i in range(self.NUM_PRODUCTION_LINES_PER_PLANT):
                # Ensure BEVERAGE_LINE_A is created in the first plant for WriteDataApp machine
                if plant_idx == 0 and i == 0:
                    line_type = "BEVERAGE_LINE_A"
                    line_id = "BEVERAGE_LINE_A"  # Use WriteDataApp production line name
                    beverage_line_a_created = True
                else:
                    # Avoid creating duplicate BEVERAGE_LINE_A
                    available_types = [t for t in self.PRODUCTION_LINE_TYPES if t != "BEVERAGE_LINE_A" or not beverage_line_a_created]
                    line_type = random.choice(available_types)
                    line_id = f"{plant['plant_id']}_LINE_{i+1:02d}"
                
                production_line = {
                    "production_line_id": line_id,
                    "plant_id": plant["plant_id"],  # Reference to parent plant
                    "name": f"{line_type.replace('_', ' ').title()}",
                    "line_type": line_type,
                    "status": "operational" if line_type == "BEVERAGE_LINE_A" else random.choice(["operational", "maintenance", "idle"]),
                    "created_date": datetime.utcnow().isoformat() + "Z"
                }
                
                # Add basic product info for BEVERAGE_LINE_A (identity info, not metrics)
                if line_type == "BEVERAGE_LINE_A":
                    production_line["primary_product"] = "carbonated_beverage"
                
                production_lines.append(production_line)
                
                # Store in Couchbase
                self._store_document_directly(
                    production_line['production_line_id'],
                    production_line,
                    "production_lines"
                )
        
        logger.info(f"Generated {len(production_lines)} production lines (including WriteDataApp BEVERAGE_LINE_A)")
        return production_lines
    
    def generate_machines(self, production_lines: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """Generate machine documents with production line and plant references."""
        machines = []
        machine_counter = 1
        writedata_machine_created = False
        
        for line in production_lines:
            # Check if this is BEVERAGE_LINE_A and create WriteDataApp machine first
            if line["line_type"] == "BEVERAGE_LINE_A" and not writedata_machine_created:
                # Create the specific WriteDataApp machine (MACHINE_001)
                writedata_machine = self._create_writedata_machine(line)
                machines.append(writedata_machine)
                self.generated_machines.append(writedata_machine)
                
                # Store in Couchbase
                self._store_document_directly(
                    writedata_machine['machine_id'],
                    writedata_machine,
                    "machines"
                )
                writedata_machine_created = True
                logger.info(f"✅ Created WriteDataApp machine: {writedata_machine['machine_id']} ({writedata_machine['device_id']})")
                
                # Generate remaining machines for this line (NUM_MACHINES_PER_LINE - 1)
                machines_to_generate = self.NUM_MACHINES_PER_LINE - 1
            else:
                machines_to_generate = self.NUM_MACHINES_PER_LINE
            
            for i in range(machines_to_generate):
                machine_id = f"MACHINE_{machine_counter:03d}"
                # Skip MACHINE_001 since it's the WriteDataApp machine
                if machine_id == "MACHINE_001":
                    machine_counter += 1
                    machine_id = f"MACHINE_{machine_counter:03d}"
                
                machine_type = random.choice(self.MACHINE_TYPES)
                product_type = random.choice(self.PRODUCT_TYPES)
                
                # Generate device_id following WriteDataApp pattern
                device_id = f"rpi-{machine_id.lower().replace('_', '-')}-01"
                
                machine = {
                    "machine_id": machine_id,
                    "device_id": device_id,
                    "production_line_id": line["production_line_id"],  # Reference to parent line
                    "plant_id": line["plant_id"],  # Reference to grandparent plant
                    "machine_type": machine_type,
                    "product_type": product_type,
                    "name": f"{machine_type.replace('_', ' ').title()} {machine_counter}",
                    "model": f"Model_{random.randint(100, 999)}",
                    "serial_number": f"SN{random.randint(100000, 999999)}",
                    "status": random.choice(["operational", "maintenance", "warning", "error"]),
                    "created_date": datetime.utcnow().isoformat() + "Z"
                }
                
                machines.append(machine)
                self.generated_machines.append(machine)  # Track for alert generation
                
                # Store in Couchbase
                self._store_document_directly(
                    machine['machine_id'],
                    machine,
                    "machines"
                )
                
                machine_counter += 1
        
        logger.info(f"Generated {len(machines)} machines (including WriteDataApp machine)")
        return machines
    
    def _create_writedata_machine(self, beverage_line: Dict[str, Any]) -> Dict[str, Any]:
        """Create the specific machine referenced in WriteDataApp - basic identity only."""
        return {
            "machine_id": "MACHINE_001",
            "device_id": "rpi-cpg-line1-01",
            "production_line_id": beverage_line["production_line_id"],  # Reference to BEVERAGE_LINE_A
            "plant_id": beverage_line["plant_id"],  # Reference to parent plant
            "machine_type": "beverage_filling_line",
            "product_type": "carbonated_beverage",
            "name": "Primary CPG Beverage Filling Line",
            "model": "CPG-BFL-2024-ULTIMATE",
            "serial_number": "SN001001",
            "status": "operational",
            "created_date": datetime.utcnow().isoformat() + "Z"
        }
    
    def generate_alerts(self, machines=None, count: int = None) -> List[Dict[str, Any]]:
        """Generate sample alerts using WriteDataApp schema for additional machines."""
        if not self.alert_templates:
            logger.warning("No alert templates available, skipping alert generation")
            return []
        
        if count is None:
            count = self.NUM_ALERTS
            
        alerts = []
        
        # Create alerts for additional machines (MACHINE_001 generates live alerts via WriteDataApp)
        for _ in range(count):
            machine_config = random.choice(self.ADDITIONAL_MACHINES)
            alert_template = random.choice(self.alert_templates)
            
            # Generate alert using WriteDataApp schema
            alert = self._create_writedata_style_alert(machine_config, alert_template)
            alerts.append(alert)
            
            # Store in Couchbase
            self._store_document_directly(
                alert["alert_id"],
                alert,
                "alerts"
            )
        
        logger.info(f"Generated {len(alerts)} alerts using WriteDataApp schema")
        return alerts
    
    def _create_writedata_style_alert(self, machine_config: Dict[str, str], alert_template: Dict[str, str]) -> Dict[str, Any]:
        """Create alert using WriteDataApp schema format."""
        self.alert_counter += 1
        
        # 3 days ago timestamp
        timestamp_3_days_ago = datetime.utcnow() - timedelta(days=3)
        timestamp = timestamp_3_days_ago.isoformat() + "Z"
        
        # Format alert_id like WriteDataApp
        alert_id = f"CPG_ALERT_{timestamp_3_days_ago.strftime('%Y%m%d')}_{self.alert_counter:06d}"
        
        # Generate device_id based on machine_id
        device_id = f"rpi-{machine_config['machine_id'].lower().replace('_', '-')}-01"
        
        # Get product code for this machine type
        product_codes = self.PRODUCT_CODES.get(machine_config["product_type"], ["UNKNOWN_PRODUCT"])
        product_code = random.choice(product_codes)
        
        # Create base alert following WriteDataApp pattern
        alert = {
            "alert_id": alert_id,
            "device_id": device_id,
            "machine_id": machine_config["machine_id"],
            "machine_type": machine_config["machine_type"],
            "error_code": alert_template["error_code"],
            "severity": alert_template["severity"].lower(),
            "status": "active",
            "timestamp": timestamp,
            "production_line": machine_config["production_line"],
            "product_type": machine_config["product_type"],
            "batch_number": random.randint(1001, 1500),
            "product_code": product_code,
            "solution_comment": alert_template["solution_comment"],
            "escalation_required": alert_template["severity"].upper() in ["CRITICAL", "HIGH"],
            "context": {
                "temperature": round(random.uniform(20.0, 100.0), 1),
                "pressure": round(random.uniform(0.5, 15.0), 1),
                "speed": round(random.uniform(50.0, 3000.0), 1),
                "humidity": round(random.uniform(30.0, 70.0), 1),
                "oee": round(random.uniform(0.7, 0.95), 3)
            }
        }
        
        # Add optional fields based on error type
        if random.random() > 0.3:
            alert["current_value"] = round(random.uniform(50.0, 150.0), 2)
            alert["threshold"] = round(random.uniform(80.0, 120.0), 2)
        
        # Add acknowledged/resolved information for some alerts
        if random.random() > 0.4:
            alert["acknowledged_by"] = random.choice(self.OPERATORS)
            if random.random() > 0.6:
                alert["resolved_by"] = random.choice(self.TECHNICIANS)
                alert["status"] = "resolved"
                alert["resolution_time"] = (timestamp_3_days_ago + timedelta(hours=random.randint(1, 24))).isoformat() + "Z"
        
        return alert
    
    def generate_all_sample_data(self) -> Dict[str, Any]:
        """Generate all sample data for the demo - fully aligned with WriteDataApp."""
        logger.info("Starting sample data generation (aligned with WriteDataApp)...")
        
        # Generate plants (identity only, primary plant hosts WriteDataApp)
        plants = self.generate_plants()
        
        # Generate production lines (BEVERAGE_LINE_A for WriteDataApp machine)
        production_lines = self.generate_production_lines(plants)
        
        # Generate machines (including WriteDataApp MACHINE_001 machine)
        machines = self.generate_machines(production_lines)
        
        # Generate alerts using original WriteDataApp schema
        alerts = self.generate_alerts()
        
        summary = {
            "plants": len(plants),
            "production_lines": len(production_lines),
            "machines": len(machines),
            "alerts": len(alerts)
        }
        
        logger.info(f"Sample data generation complete: {summary}")
        logger.info("✅ WriteDataApp machine MACHINE_001 created in BEVERAGE_LINE_A")
        logger.info("✅ Machine collection contains identity information only")
        logger.info("Note: Solutions are generated by a separate script")
        return summary 