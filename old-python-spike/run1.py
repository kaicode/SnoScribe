import ollama
import time

class OllamaProcessor:
    def __init__(self, model_name="gemma3:12b"):
        """
        Initializes the OllamaProcessor.

        Args:
            model_name: The name of the Ollama model to use.
        """
        self.model_name = model_name
        self.client = ollama.Client()  # Create a client instance only once

    def process_documents(self, system_prompt, documents):
        """
        Sends a system prompt and a list of documents to the Ollama API for processing.

        Args:
            system_prompt: The system prompt to send.
            documents: A list of documents (strings) to process.

        Returns:
            A list of responses from the Ollama API.
        """
        try:
            # Construct the conversation
            conversation = [
                {"role": "system", "content": system_prompt}
            ]

            responses = []
            for document in documents:
                # Add the document as a user message
                conversation.append({"role": "user", "content": document})

                try:
                    # Send the conversation to the Ollama API
                    response = self.client.chat(model=self.model_name, messages=conversation)
                    # Extract the content from the response
                    content = response['message']['content']
                    responses.append(content)
                    print(f"Response to document '{document[:20]}...': {content[:50]}...") # print first 50 chars of response
                    # Clear the conversation after each document. Keeps context short and clean.
                    conversation = [{"role": "system", "content": system_prompt}]

                except Exception as e:
                    print(f"Error processing document '{document[:20]}...': {e}")
                    responses.append(f"Error: {e}") # add error message to responses

            return responses

        except Exception as e:
            print(f"Error initializing or processing documents: {e}")
            return [f"Error: {e}"]

# Example Usage:
if __name__ == "__main__":
    # Set your system prompt
    system_prompt = "You are a helpful assistant summarizing documents."

    # Example documents
    documents = [
        "This is the first document. It's about the history of programming languages.",
        "The second document discusses the benefits of cloud computing.",
        "The third document explains the principles of machine learning.",
        "This is a long document about quantum physics and its applications."
    ]

    processor = OllamaProcessor()  # Use the default model (llama2)

    responses = processor.process_documents(system_prompt, documents)

    print("\n--- All Responses ---")
    for i, response in enumerate(responses):
        print(f"Document {i+1}: {response}")
