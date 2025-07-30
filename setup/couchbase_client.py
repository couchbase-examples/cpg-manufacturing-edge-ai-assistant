import logging
from typing import Dict, Any, Optional, List
from datetime import datetime
from couchbase.cluster import Cluster
from couchbase.auth import PasswordAuthenticator
from couchbase.options import ClusterOptions
from couchbase.exceptions import CouchbaseException, QueryIndexAlreadyExistsException
from couchbase.scope import Scope
from couchbase.management.search import SearchIndex
from config import settings

logger = logging.getLogger(__name__)

# Constants
DEFAULT_EMBEDDING_DIMS = 768

def serialize_datetime(obj: Any) -> Any:
    """Custom serializer for datetime objects."""
    if isinstance(obj, datetime):
        return obj.isoformat()
    elif isinstance(obj, dict):
        return {key: serialize_datetime(value) for key, value in obj.items()}
    elif isinstance(obj, list):
        return [serialize_datetime(item) for item in obj]
    else:
        return obj

class CouchbaseClient:
    """Couchbase client for CPG manufacturing data management with multi-collection support."""
    
    def __init__(self):
        self.cluster = None
        self.bucket = None
        self.scope: Optional[Scope] = None
        self.collections: Dict[str, Any] = {}  # Dictionary to store different collections
        self.connect()

    def _build_collection_path(self, collection_name: str, scope_name: str = None) -> str:
        """Build the full collection path for N1QL queries."""
        scope_name = scope_name or settings.couchbase_scope_name
        bucket_name = settings.couchbase_bucket_name
        return f"`{bucket_name}`.`{scope_name}`.`{collection_name}`"

    def _get_existing_scopes(self) -> List[Any]:
        """Get all existing scopes from the bucket."""
        bucket_manager = self.bucket.collections()
        return bucket_manager.get_all_scopes()

    def _scope_exists(self, scope_name: str) -> bool:
        """Check if a scope exists."""
        existing_scopes = self._get_existing_scopes()
        return any(scope.name == scope_name for scope in existing_scopes)

    def _get_existing_collection_names(self, scope_name: str) -> set:
        """Get existing collection names in a scope."""
        existing_scopes = self._get_existing_scopes()
        target_scope = next((scope for scope in existing_scopes if scope.name == scope_name), None)
        return {coll.name for coll in target_scope.collections} if target_scope else set()

    def _create_scope_if_not_exists(self, scope_name: str, description: str = "scope") -> bool:
        """Create a scope if it doesn't exist. Returns True if successful or already exists."""
        try:
            if not self._scope_exists(scope_name):
                logger.info(f"Creating {description}: {scope_name}")
                bucket_manager = self.bucket.collections()
                bucket_manager.create_scope(scope_name)
                logger.info(f"✅ {description.capitalize()} '{scope_name}' created successfully")
            else:
                logger.info(f"✅ {description.capitalize()} '{scope_name}' already exists")
            return True
        except Exception as e:
            logger.error(f"❌ Error checking/creating {description}: {e}")
            return False

    def _create_collection_if_not_exists(self, collection_name: str, scope_name: str, 
                                       collection_type: str = "collection") -> bool:
        """Create a collection if it doesn't exist."""
        try:
            bucket_manager = self.bucket.collections()
            logger.info(f"Creating {collection_type}: {collection_name} in scope: {scope_name}")
            bucket_manager.create_collection(
                collection_name=collection_name,
                scope_name=scope_name
            )
            logger.info(f"✅ {collection_type.capitalize()} '{collection_name}' created successfully")
            return True
        except Exception as e:
            logger.error(f"❌ Failed to create {collection_type} '{collection_name}': {e}")
            return False
    
    def connect(self) -> None:
        """Establish connection to Couchbase cluster."""
        try:
            # Create authenticator
            auth = PasswordAuthenticator(
                settings.couchbase_username,
                settings.couchbase_password
            )
            
            # Connect to cluster
            self.cluster = Cluster(
                settings.couchbase_connection_string,
                ClusterOptions(auth)
            )
            
            # Get bucket and scope
            self.bucket = self.cluster.bucket(settings.couchbase_bucket_name)
            self.scope = self.bucket.scope(settings.couchbase_scope_name)
            
            # Initialize all collections (assuming they exist)
            self._initialize_collections()
            
            logger.info(f"Successfully connected to Couchbase bucket: {settings.couchbase_bucket_name}, scope: {settings.couchbase_scope_name}")
            logger.info(f"Initialized collections: {list(settings.couchbase_collections.keys())}")
            
        except CouchbaseException as e:
            logger.error(f"Failed to connect to Couchbase: {e}")
            raise

    def _initialize_collections(self) -> None:
        """Initialize all collections."""
        for collection_type, collection_name in settings.couchbase_collections.items():
            try:
                self.collections[collection_type] = self.scope.collection(collection_name)
                logger.debug(f"Initialized collection '{collection_name}' for type '{collection_type}'")
            except Exception as e:
                logger.error(f"❌ Collection '{collection_name}' not accessible: {e}")
                raise CouchbaseException(f"Failed to initialize required collection '{collection_name}'")
    
    def setup_collections(self) -> None:
        """Create scope and all required collections if they don't exist."""
        try:
            self._ensure_scope_and_collections_exist()
            
            # Re-initialize collections after creation
            for collection_type, collection_name in settings.couchbase_collections.items():
                self.collections[collection_type] = self.scope.collection(collection_name)
            
            logger.info("✅ Collections setup completed")
            
        except Exception as e:
            logger.error(f"❌ Collection setup failed: {e}")
            raise

    def _ensure_scope_and_collections_exist(self) -> None:
        """Create scope and all required collections if they don't exist."""
        try:
            # Create main scope
            if not self._create_scope_if_not_exists(settings.couchbase_scope_name, "scope"):
                raise CouchbaseException(f"Failed to create or access required scope: {settings.couchbase_scope_name}")
            
            # Handle collections
            self._manage_collections(settings.couchbase_collections, settings.couchbase_scope_name)
                    
        except Exception as e:
            logger.error(f"❌ Error in scope/collection management: {e}")
            raise

    def _manage_collections(self, collections: Dict[str, str], scope_name: str) -> None:
        """Manage collections: clear existing ones or create new ones."""
        try:
            existing_collection_names = self._get_existing_collection_names(scope_name)
            
            for collection_type, collection_name in collections.items():
                if collection_name in existing_collection_names:
                    # Delete all documents from existing collection
                    self.delete_all_documents_from_collection(collection_name, scope_name)
                else:
                    # Create collection if it doesn't exist
                    self._create_collection_if_not_exists(
                        collection_name, scope_name, f"collection (for {collection_type})"
                    )
                        
        except Exception as e:
            logger.error(f"❌ Error checking/creating collections: {e}")
            logger.warning(f"⚠️ Some collections may not be available due to error: {e}")

    def get_collection(self, collection_type: str) -> Any:
        """Get a specific collection by type."""
        if collection_type in self.collections:
            return self.collections[collection_type]
        else:
            logger.error(f"❌ Collection type '{collection_type}' not found")
            raise ValueError(f"Collection type '{collection_type}' is not configured or available")

    def delete_all_documents_from_collection(self, collection_name: str, scope_name: str = None) -> bool:
        """Delete all documents from a collection using N1QL DELETE query."""
        try:
            scope_name = scope_name or settings.couchbase_scope_name
            collection_path = self._build_collection_path(collection_name, scope_name)
            
            # Use N1QL DELETE to remove all documents
            delete_query = f"DELETE FROM {collection_path}"
            
            logger.info(f"🗑️ Deleting all documents from collection: {collection_name} in scope: {scope_name}")
            result= self.cluster.query(delete_query).execute()
            logger.info(f"✅ Successfully deleted all documents from collection '{collection_name}'")
            return True
            
        except CouchbaseException as e:
            logger.error(f"❌ Failed to delete documents from collection '{collection_name}': {e}")
            return False
        except Exception as e:
            logger.error(f"❌ Unexpected error deleting documents from collection '{collection_name}': {e}")
            return False

    def create_indexes(self) -> None:
        """Create primary indexes and vector indexes for all collections."""
        try:
            # Create primary indexes for manufacturing collections
            self._create_primary_indexes_for_collections(settings.couchbase_collections, settings.couchbase_scope_name)
            
            # Create vector search indexes
            self.create_vector_indexes()
                        
        except Exception as e:
            logger.error(f"Error creating indexes: {e}")

    def _create_primary_indexes_for_collections(self, collections: Dict[str, str], scope_name: str) -> None:
        """Create primary indexes for a set of collections."""
        for collection_type, collection_name in collections.items():
            collection_path = self._build_collection_path(collection_name, scope_name)
            index_sql = f"CREATE PRIMARY INDEX ON {collection_path}"
            
            try:
                self.cluster.query(index_sql).execute()
                logger.info(f"✅ Created primary index: {index_sql}")
            except CouchbaseException as e:
                if "already exists" not in str(e):
                    logger.warning(f"❌ Failed to create primary index: {e}")

    def create_vector_indexes(self) -> None:
        """Create vector search indexes for RAG functionality."""
        try:
            logger.info("Creating vector search indexes for RAG...")
            
            # ---------- Manuals vector index (text + embeddings) -----------
            # SKIPPED: Manual collection vector index creation disabled per request
            logger.info("Skipping vector index creation for manual collection")

            # ---------- Solutions vector index (solution_comment + embeddings) -----------
            solutions_collection = settings.couchbase_collections.get("solutions")
            if solutions_collection:
                solutions_vector_index_name = f"semantic_{solutions_collection}"
                self._upsert_vector_index(
                    index_name=solutions_vector_index_name,
                    collection_name=solutions_collection,
                    embedding_dims=DEFAULT_EMBEDDING_DIMS,
                    text_field_name="solution_comment",
                    keyword_fields=["error_code"],
                )

        except QueryIndexAlreadyExistsException as e:
            logger.info(f"Vector index '{e.index_name}' already exists")
        except Exception as e:

            logger.error(f"Error in vector index creation: {e}")

    def _upsert_vector_index(self, index_name: str, collection_name: str, embedding_dims: int = DEFAULT_EMBEDDING_DIMS, text_field_name: str = "", keyword_fields: Optional[List[str]] = None) -> None:
        """Helper to (re)create a vector-search index for a given collection."""
        try:
            bucket_name = settings.couchbase_bucket_name
            scope_name = settings.couchbase_scope_name

            text_field = text_field_name or "content"
            keyword_fields = keyword_fields or []

            vector_index_definition = {
                "type": "fulltext-index",
                "name": index_name,
                "sourceName": bucket_name,
                "sourceType": "gocbcore",
                "planParams": {"index_partitions": 1, "num_replicas": 0},
                "params": {
                    "doc_config": {
                        "docid_prefix_delim": "",
                        "docid_regexp": "",
                        "mode": "scope.collection.type_field",
                        "type_field": "type",
                    },
                    "mapping": {
                        "default_analyzer": "standard",
                        "default_datetime_parser": "dateTimeOptional",
                        "default_field": "_all",
                        "default_mapping": {"dynamic": True, "enabled": False},
                        "default_type": "_default",
                        "docvalues_dynamic": False,
                        "index_dynamic": True,
                        "store_dynamic": True,
                        "type_field": "_type",
                        "types": {
                            f"{scope_name}.{collection_name}": {
                                "dynamic": False,
                                "enabled": True,
                                "properties": {
                                    f"{text_field}": {
                                        "dynamic": False,
                                        "enabled": True,
                                        "fields": [
                                            {
                                                "store": True,
                                                "index": True,
                                                "name": text_field,
                                                "type": "text",
                                            }
                                        ],
                                    },
                                    "embedding": {
                                        "dynamic": False,
                                        "enabled": True,
                                        "fields": [
                                            {
                                                "dims": embedding_dims,
                                                "index": True,
                                                "name": "embedding",
                                                "similarity": "dot_product",
                                                "type": "vector",
                                                "vector_index_optimized_for": "recall",
                                            }
                                        ],
                                    },
                                },
                            }
                        },
                    },
                },
            }

            # Add keyword analyzers for specified fields
            if keyword_fields:
                for kw in keyword_fields:
                    vector_index_definition["params"]["mapping"]["types"][f"{scope_name}.{collection_name}"]["properties"][kw] = {
                        "dynamic": False,
                        "enabled": True,
                        "fields": [
                            {
                                "name": kw,
                                "type": "text",
                                "analyzer": "keyword",
                                "index": True,
                                "store": True,
                            }
                        ],
                    }

            self.scope.search_indexes().upsert_index(SearchIndex.from_json(vector_index_definition))
            logger.info(f"✅ Vector search index '{index_name}' created/updated successfully")
        except Exception as err:
            logger.error(f"Failed to upsert vector index '{index_name}': {err}")

    def store_document(self, key: str, document: Dict[str, Any], collection_type: str = "machines") -> bool:
        """Store a document in the appropriate collection."""
        try:
            # Serialize datetime objects to ISO format strings
            serialized_document = serialize_datetime(document.copy())
            
            # Add metadata
            serialized_document['created_at'] = datetime.utcnow().isoformat()
            serialized_document['updated_at'] = datetime.utcnow().isoformat()
            
            collection = self.get_collection(collection_type)
            collection.upsert(key, serialized_document)
            logger.debug(f"Stored document with key: {key} in {collection_type} collection")
            return True
            
        except CouchbaseException as e:
            logger.error(f"Failed to store document {key} in {collection_type}: {e}")
            return False

    def store_manual_chunk(self, chunk_id: str, content: str, 
                          metadata: Dict[str, Any]) -> bool:
        """Store a processed manual chunk with embeddings in manuals collection."""
        document = {
            'type': 'manual_chunk',
            'chunk_id': chunk_id,
            'content': content,
            'metadata': metadata,
            'machine_type': metadata.get('machine_type', 'Ultima_SV')
        }
        
        return self.store_document(f"manual_chunk_{chunk_id}", document, collection_type="manuals")

    def close(self) -> None:
        """Close the Couchbase connection."""
        if self.cluster:
            self.cluster.close()
            logger.info("Disconnected from Couchbase") 