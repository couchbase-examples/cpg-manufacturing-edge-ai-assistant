import logging
from typing import List, Dict, Any, Optional
from pathlib import Path

# LangChain imports for document processing pipeline (keeping text processing only)
from langchain_community.document_loaders import TextLoader
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_core.documents import Document

import numpy as np
from couchbase_client import CouchbaseClient
from document_utils import extract_manufacturing_keywords
from config import settings

logger = logging.getLogger(__name__)

class DocumentProcessor:
    """Process and chunk documents for direct Couchbase storage using LangChain text processing pipeline."""
    
    def __init__(self, couchbase_client: CouchbaseClient):
        self.couchbase_client = couchbase_client
        
        # Initialize LangChain text splitter with optimized settings
        self.text_splitter = RecursiveCharacterTextSplitter(
            chunk_size=1000,
            chunk_overlap=200,
            length_function=len,
            separators=[
                "\n\n",  # Paragraph breaks
                "\n",    # Line breaks
                ". ",    # Sentence endings
                " ",     # Words
                ""       # Characters
            ],
            add_start_index=True  # Track position in original document
        )
        
        logger.info("✅ Document processor initialized for direct Couchbase storage (no embeddings)")
        
    def load_text_documents(self, text_path: str) -> List[Document]:
        """Load text documents using LangChain TextLoader."""
        try:
            if not Path(text_path).exists():
                raise FileNotFoundError(f"Text file not found: {text_path}")
            
            # Use LangChain's TextLoader
            loader = TextLoader(text_path, encoding='utf-8')
            documents = loader.load()
            
            logger.info(f"📄 Loaded {len(documents)} documents from text file: {text_path}")
            
            # Add source metadata to each document
            for i, doc in enumerate(documents):
                doc.metadata.update({
                    'source_file': text_path,
                    'document_index': i,
                    'document_type': 'manual',
                    'loader': 'TextLoader'
                })
            
            return documents
            
        except Exception as e:
            logger.error(f"❌ Error loading text file with LangChain TextLoader: {e}")
            return []
    
    def split_documents(self, documents: List[Document]) -> List[Document]:
        """Split documents into chunks using LangChain text splitter."""
        try:
            # Use LangChain's RecursiveCharacterTextSplitter
            chunks = self.text_splitter.split_documents(documents)
            
            logger.info(f"📝 Split {len(documents)} documents into {len(chunks)} chunks")
            
            # Enhance metadata with manufacturing-specific information
            for i, chunk in enumerate(chunks):
                # Add chunk-specific metadata
                chunk.metadata.update({
                    'chunk_index': i,
                    'chunk_id': f"manual_chunk_{i:04d}",
                    'word_count': len(chunk.page_content.split()),
                    'char_count': len(chunk.page_content),
                    'contains_procedure': self._contains_procedure(chunk.page_content),
                    'keywords': self._extract_keywords(chunk.page_content),
                    'processing_timestamp': str(np.datetime64('now')),
                    'storage_type': 'direct_couchbase'  # Indicate storage method
                })
            
            return chunks
            
        except Exception as e:
            logger.error(f"❌ Error splitting documents: {e}")
            return []
    
    def _contains_procedure(self, text: str) -> bool:
        """Check if text contains procedural information."""
        procedure_keywords = [
            'procedure', 'step', 'instruction', 'warning', 'caution',
            'maintenance', 'troubleshoot', 'error', 'alarm', 'fault',
            'safety', 'emergency', 'operation', 'start', 'stop'
        ]
        text_lower = text.lower()
        return any(keyword in text_lower for keyword in procedure_keywords)
    
    def _extract_keywords(self, text: str) -> List[str]:
        """Extract relevant manufacturing keywords from text."""
        return extract_manufacturing_keywords(text)
    
    def process_manual(self, text_path: str) -> bool:
        """Process text manual using LangChain pipeline and store directly in Couchbase."""
        try:
            # Step 1: Load text documents using LangChain
            logger.info("🔄 Step 1: Loading text documents...")
            documents = self.load_text_documents(text_path)
            
            if not documents:
                logger.error("❌ No documents loaded from text file")
                return False
            
            # Step 2: Split documents into chunks using LangChain
            logger.info("🔄 Step 2: Splitting documents into chunks...")
            chunks = self.split_documents(documents)
            
            if not chunks:
                logger.error("❌ No chunks created from documents")
                return False
            
            # Step 3: Store chunks directly in Couchbase (no embeddings)
            logger.info(f"🔄 Step 3: Storing {len(chunks)} chunks directly in Couchbase...")
            
            stored_count = 0
            for chunk in chunks:
                try:
                    # Store chunk using the manual chunk storage method
                    chunk_id = chunk.metadata['chunk_id']
                    success = self.couchbase_client.store_manual_chunk(
                        chunk_id=chunk_id,
                        content=chunk.page_content,
                        metadata=chunk.metadata
                    )
                    if success:
                        stored_count += 1
                except Exception as e:
                    logger.error(f"Failed to store chunk {chunk.metadata.get('chunk_id', 'unknown')}: {e}")
            
            logger.info(f"✅ Successfully stored {stored_count}/{len(chunks)} chunks directly in Couchbase")
            logger.info("🎯 Pipeline completed: Text → Chunks → Direct Couchbase Storage")
            
            return stored_count > 0
            
        except Exception as e:
            logger.error(f"❌ Error in document processing pipeline: {e}")
            return False
    
        """Search for text content in Couchbase using SQL++ queries (no vector search)."""
        try:
            logger.info(f"🔍 Searching text content for query: '{query}'")
            
            # Build SQL++ query for text search
            bucket_name = settings.couchbase_bucket_name
            scope_name = settings.couchbase_scope_name
            collection_name = settings.couchbase_collections.get(collection_type, "manuals")
            
            # Search query using CONTAINS or LIKE for text matching
            search_query = f"""
            SELECT META().id as document_id, content, metadata, type
            FROM `{bucket_name}`.`{scope_name}`.`{collection_name}` 
            WHERE type = 'manual_chunk' 
            AND (LOWER(content) CONTAINS LOWER($query)
                 OR ANY keyword IN metadata.keywords SATISFIES LOWER(keyword) CONTAINS LOWER($query) END)
            ORDER BY metadata.chunk_index
            LIMIT 10
            """
            
            # Execute query
            result = self.couchbase_client.cluster.query(
                search_query, 
                query=query
            )
            
            chunks = []
            for row in result:
                chunk_data = {
                    'document_id': row['document_id'],
                    'content': row['content'],
                    'metadata': row['metadata'],
                    'type': row['type']
                }
                chunks.append(chunk_data)
            
            logger.info(f"🔍 Found {len(chunks)} text chunks for query: '{query}'")
            return chunks
            
        except Exception as e:
            logger.error(f"❌ Error searching text content: {e}")
            return []
    
        """Search with additional filters using SQL++ queries."""
        try:
            logger.info(f"🔍 Searching with filters for query: '{query}'")
            
            # Build SQL++ query with filters
            bucket_name = settings.couchbase_bucket_name
            scope_name = settings.couchbase_scope_name
            collection_name = settings.couchbase_collections.get("manuals", "manuals")
            
            # Base query
            where_conditions = ["type = 'manual_chunk'"]
            
            # Add text search condition
            where_conditions.append("(LOWER(content) CONTAINS LOWER($query) OR ANY keyword IN metadata.keywords SATISFIES LOWER(keyword) CONTAINS LOWER($query) END)")
            
            # Add filter conditions
            if filters:
                if filters.get('procedure_only'):
                    where_conditions.append("metadata.contains_procedure = true")
                if 'document_index' in filters:
                    where_conditions.append(f"metadata.document_index = {filters['document_index']}")
                if 'keywords' in filters:
                    where_conditions.append(f"ANY keyword IN metadata.keywords SATISFIES LOWER(keyword) CONTAINS LOWER('{filters['keywords'].lower()}') END")
            
            search_query = f"""
            SELECT META().id as document_id, content, metadata, type
            FROM `{bucket_name}`.`{scope_name}`.`{collection_name}` 
            WHERE {' AND '.join(where_conditions)}
            ORDER BY metadata.chunk_index
            LIMIT {max_chunks}
            """
            
            # Execute query
            result = self.couchbase_client.cluster.query(
                search_query, 
                query=query
            )
            
            chunks = []
            for row in result:
                chunk_data = {
                    'document_id': row['document_id'],
                    'content': row['content'],
                    'metadata': row['metadata'],
                    'type': row['type']
                }
                chunks.append(chunk_data)
            
            logger.info(f"🔍 Found {len(chunks)} chunks with filters")
            return chunks
            
        except Exception as e:
            logger.error(f"❌ Error searching with filters: {e}")
            return []
    
        """Get all stored manual chunks from Couchbase."""
        try:
            bucket_name = settings.couchbase_bucket_name
            scope_name = settings.couchbase_scope_name
            collection_name = settings.couchbase_collections.get(collection_type, "manuals")
            
            query = f"""
            SELECT META().id as document_id, content, metadata, type
            FROM `{bucket_name}`.`{scope_name}`.`{collection_name}` 
            WHERE type = 'manual_chunk'
            ORDER BY metadata.chunk_index
            LIMIT {limit}
            """
            
            result = self.couchbase_client.cluster.query(query)
            
            chunks = []
            for row in result:
                chunk_data = {
                    'document_id': row['document_id'],
                    'content': row['content'],
                    'metadata': row['metadata'],
                    'type': row['type']
                }
                chunks.append(chunk_data)
            
            logger.info(f"📄 Retrieved {len(chunks)} manual chunks from Couchbase")
            return chunks
            
        except Exception as e:
            logger.error(f"❌ Error retrieving chunks: {e}")
            return []

        """Get statistics about the processed documents."""
        try:
            bucket_name = settings.couchbase_bucket_name
            scope_name = settings.couchbase_scope_name
            collection_name = settings.couchbase_collections.get("manuals", "manuals")
            
            # Count total chunks
            count_query = f"""
            SELECT COUNT(*) as total_chunks
            FROM `{bucket_name}`.`{scope_name}`.`{collection_name}` 
            WHERE type = 'manual_chunk'
            """
            
            result = self.couchbase_client.cluster.query(count_query)
            total_chunks = list(result)[0]['total_chunks'] if result else 0
            
            stats = {
                "storage_method": "direct_couchbase",
                "total_chunks": total_chunks,
                "text_splitter": "RecursiveCharacterTextSplitter",
                "chunk_size": 1000,
                "chunk_overlap": 200,
                "embeddings_enabled": False,
                "vector_search_enabled": False,
                "langchain_text_processing": True
            }
            
            return stats
            
        except Exception as e:
            logger.error(f"❌ Error getting processing stats: {e}")
            return {"error": str(e)} 