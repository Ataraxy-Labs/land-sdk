defmodule Useland.MixProject do
  use Mix.Project

  def project do
    [
      app: :useland,
      version: "0.1.0",
      elixir: "~> 1.18",
      description: "Typed wrappers over the land CLI's --json output",
      package: [licenses: ["MIT"], links: %{"land" => "https://useland.dev"}],
      deps: []
    ]
  end

  def application, do: [extra_applications: []]
end
