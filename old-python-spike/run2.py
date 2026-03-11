import requests
import json
import os
import logging

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')


class OllamaProcessorHTTP:
    def __init__(self, model_name="gemma3:12b", api_url="http://localhost:11434/api/chat"):
        """
        Initializes the OllamaProcessorHTTP.

        Args:
            model_name: The name of the Ollama model to use.
            api_url: The URL of the Ollama API endpoint.  Defaults to localhost.
        """
        self.model_name = model_name
        self.api_url = api_url

    def process_document(self, system_prompt, document):
        """
        Sends a system prompt and a list of documents to the Ollama API using HTTP calls.

        Args:
            system_prompt: The system prompt to send.
            documents: A list of documents (strings) to process.

        Returns:
            A list of responses from the Ollama API.
        """
        response = None
        try:
            # Make the HTTP request
            headers = {"Content-Type": "application/json"}
            conversation = [
                { "role": "user", "content": system_prompt},
                { "role": "user", "content": document},
            ]
            data = json.dumps({"model": self.model_name, "stream": False, "messages": conversation})
            # data = json.dumps({"model": self.model_name, "stream": False, "prompt": prompt})
            # data = json.dumps({"model": self.model_name, "stream": False, "prompt": "You are a clinical NLP assistant. Extract clinical conditions from the following note:\n\nPatient reports sudden onset of ear pain and decreased hearing in right ear."})

            logging.debug(f"REQUEST: {data}")
            response = requests.post(self.api_url, headers=headers, data=data) # stream=False to avoid stream processing

            response.raise_for_status()  # Raise HTTPError for bad responses (4xx or 5xx)
            # logging.debug(f"RESPONSE: {response.text}")
            json_response = response.json()
            content = json_response['message']['content']
            return content
        except requests.exceptions.RequestException as e:
            print(f"Error processing document '{document[:20]}...': {e}")
            print(response.text)
            responses.append(f"Error: {e}")

if __name__ == "__main__":
    # Set your system prompt
    # system_prompt = "You are a helpful assistant summarizing documents. Process the following document:\n"
    with open("prompt.txt", "r") as f:
        system_prompt = f.read().strip()

    processor = OllamaProcessorHTTP()

    # Example documents
#    documents = [
#        "This is the first document. It's about the history of programming languages.",
#        "The second document discusses the benefits of cloud computing.",
#        "The third document explains the principles of machine learning.",
#        "This is a long document about quantum physics and its applications."
#    ]
    notes_dir = "notes"
    for filename in os.listdir(notes_dir):
        if filename.endswith(".txt"):
            filepath = os.path.join(notes_dir, filename)
            try:
                with open(filepath, "r") as f:
                    content = f.read().strip()
                    print("\n--- Document ---")
                    print(f"{content}")
                    print("\n--- End of Document ---")

                    #print(f"Content of {filename}: {content}")
                    response = processor.process_document(system_prompt, content)
                    print("\n--- Response ---")
                    print(f"{response}")
                    break
            except Exception as e:
                print(f"Error reading {filename}: {e}")


